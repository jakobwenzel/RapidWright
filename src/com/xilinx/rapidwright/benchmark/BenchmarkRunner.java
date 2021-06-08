package com.xilinx.rapidwright.benchmark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xilinx.rapidwright.device.TileTypeEnum;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.Job;
import com.xilinx.rapidwright.util.JobQueue;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.util.performance_evaluation.TimingResults;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;

public class BenchmarkRunner {

    private static Stream<SomeRun> getAllRuns(Collection<Benchmark> benchmarks, List<PlacerType> placers) {

        return benchmarks.stream()
                .flatMap(b-> Stream.concat(
                    Stream.of(b.getRegularRun()),
                    placers.stream().map(p->new BenchmarkRun(b,p))
                )).distinct();
    }

    public static void main(String[] args) throws IOException {

        OptionParser optionParser = new OptionParser();
        //Modes
        final OptionSpecBuilder runOption = optionParser
                .accepts("run", "Run a Benchmark");
        final OptionSpecBuilder lsfOption = optionParser
                .accepts("lsf", "Run matching Benchmarks on LSF");
        final OptionSpecBuilder resultsOption = optionParser
                .accepts("results", "Show Results");

        optionParser.mutuallyExclusive(runOption, lsfOption, resultsOption);

        //Always Arguments

        ArgumentAcceptingOptionSpec<Path> workdirOption = optionParser
                .accepts("workdir", "Work Directory")
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter(PathProperties.DIRECTORY_EXISTING))
                .required();

        ArgumentAcceptingOptionSpec<Benchmark> benchmarkOption = optionParser
                .accepts("benchmark", "Benchmark ID")
                .withRequiredArg()
                .withValuesConvertedBy(BenchmarkConverter.getInstance());
        ArgumentAcceptingOptionSpec<String> regularOption = optionParser
                .accepts("regular", "Regular run")
                .withRequiredArg();
        ArgumentAcceptingOptionSpec<Path> cacheOption = optionParser
                .accepts("cache", "Cache Directory")
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter(PathProperties.DIRECTORY_EXISTING))
                .required();
        ArgumentAcceptingOptionSpec<PlacerType> placerOption = optionParser
                .accepts("placer", "Placer")
                .withRequiredArg()
                .withValuesConvertedBy(PlacerType.createConverter());
        OptionSpecBuilder reuseMaxFreqOption = optionParser
                .accepts("reuseMaxFreq", "Reuse Maximum Frequency Data");
        OptionSpecBuilder evalOnlyOption = optionParser
                .accepts("evalOnly", "Only do performance evaluation");


        OptionSet options;
        try {
            options = optionParser.parse(args);
            if (!options.has(runOption) && !options.has(lsfOption) && !options.has(resultsOption)) {
                throw new RuntimeException("No mode given");
            }
        } catch (RuntimeException e) {
            try {
                optionParser.printHelpOn(System.out);
            } catch (IOException ioException) {
                throw new UncheckedIOException(ioException);
            }
            throw e;
        }

        Path workDir = options.valueOf(workdirOption);
        Path cache = options.valueOf(cacheOption);
        Benchmark benchmark = options.valueOf(benchmarkOption);
        PlacerType placer = options.valueOf(placerOption);
        boolean reuseMaxFreq = options.has(reuseMaxFreqOption);
        boolean evalOnly = options.has(evalOnlyOption);

        if (options.has(runOption)) {
            run(workDir, cache, findRun(benchmark, placer, options.valueOf(regularOption)), reuseMaxFreq, evalOnly);
        } else if (options.has(lsfOption)) {
            String regular = options.valueOf(regularOption);
            if (regular == null) {
                runLsf(workDir, cache, getBenchmarkList(benchmark), getPlacerList(placer), reuseMaxFreq, evalOnly);
            } else {
                runLsf(workDir, cache, Stream.of(findRegularRun(regular)),reuseMaxFreq, evalOnly);
            }
        } else if (options.has(resultsOption)) {
            showResults(workDir, getBenchmarkList(benchmark), getPlacerList(placer));
        } else{
            throw new RuntimeException("no mode");
        }
    }

    private static void resultsTable(Path output, Map<SomeRun, BenchmarkResult> results, Function<BenchmarkResult, String> extractor, boolean onlyOne) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(output))) {
            List<Benchmark> benchmarks = results.keySet().stream()
                    .filter(r -> r instanceof BenchmarkRun)
                    .map(r -> ((BenchmarkRun) r).benchmark)
                    .distinct()
                    .sorted(Comparator.comparing(Benchmark::getId))
                    .collect(Collectors.toList());
        /*final Map<Benchmark, List<SomeRun>> byBenchmark = results.keySet().stream().filter(r -> r instanceof BenchmarkRun)
                .collect(Collectors.groupingBy(r -> ((BenchmarkRun) r).benchmark));*/

            pw.print("Benchmark");
            if (!onlyOne) {
                for (PlacerType value : PlacerType.values()) {
                    pw.print("\t" + value);
                }
                pw.println("\tRegular");
            } else {
                pw.println();
            }

            benchmarks.forEach(benchmark -> {
                pw.print(benchmark.getId().replaceAll("rwExport","bottom_up"));

                for (PlacerType value : PlacerType.values()) {
                    final BenchmarkRun benchmarkRun = new BenchmarkRun(benchmark, value);
                    BenchmarkResult result = results.get(benchmarkRun);
                    if (result == null) {
                        throw new NullPointerException("did not find result for " + benchmarkRun);
                    }
                    pw.print("\t" + extractor.apply(result));
                    if (onlyOne) {
                        break;
                    }
                }

                if (!onlyOne) {
                    final BenchmarkResult regular = results.get(benchmark.getRegularRun());
                    if (regular == null) {
                        throw new NullPointerException("did not find regular result for " + benchmark);
                    }
                    pw.println("\t" + extractor.apply(regular));
                } else {
                    pw.println();
                }

            });
        }

    }

    private static void resultsTableExceptionCheck(Path output, Map<SomeRun, BenchmarkResult> results, Function<BenchmarkResult, String> extractor, boolean onlyOne) throws IOException {

        resultsTable(output, results, br -> {
            if (br.exception != null) {
                if (br.exception.contains("PBlockGenerator couldn't match a compatible pattern with ")) {
                    return "pblock";
                }
                if (br.exception.contains("The packing of LUTRAM/SRL instances into capable slices could not be obeyed.")) {
                    return "overfull";
                }
                if (br.exception.contains("not yet run")) {
                    return "notRun";
                }
                if (br.exception.contains(" no routed dcp found for cache id ")) {
                    return "noModule";
                }
                if (br.exception.contains("Java crash log")) {
                    return "crash";
                }
                return "unknown";
                //throw new RuntimeException("Uncategorized: "+br.exception);
            }
            return extractor.apply(br);
        }, onlyOne);
    }

    private static void showResults(Path workDir, List<Benchmark> benchmarks, List<PlacerType> placers) {
        Map<SomeRun, BenchmarkResult> results = getAllRuns(benchmarks, placers).collect(Collectors.toMap(Function.identity(), run -> {
            final Path jobDir = run.getJobDir(workDir);
            final Path json = jobDir.resolve("benchmark.json");
            return BenchmarkResult.fromJson(run, json);
        }, (a,b)->{throw new RuntimeException("cannot merge");}, TreeMap::new));

        /*final TreeMap<String, List<SomeRun>> byGroup = results.keySet().stream()
                .sorted(Comparator.comparing(SomeRun::getId))
                .collect(Collectors.groupingBy(SomeRun::getRunGroup, TreeMap::new, Collectors.toList()));
        byGroup.forEach((g,runs) -> {
            runs.forEach(p-> {
                final BenchmarkResult res = results.get(p);
                String s = res.toString();
                if (res.exception == null) {
                    s += " "+res.getEvalData(workDir);
                }
                System.out.println(p.getId() + ": " + s);
            });
            System.out.println();
        });*/
        /*
        final Map<Boolean, List<SomeRun>> byExc = results.keySet().stream()
                .collect(Collectors.partitioningBy(p -> results.get(p).exception == null));

        for (SomeRun p : byExc.get(true)) {
            System.out.println(p.getId()+": "+results.get(p));
        }
        System.out.println();
        System.out.println();
        byExc.get(false).stream().sorted(Comparator.comparing(p->results.get(p).exception)).forEach(p-> {
            System.out.println(p.getId()+": "+results.get(p));
        });*/

        try {
            Path evalDir = workDir.resolve("evaluation");
            Files.createDirectories(evalDir);
            resultsTableExceptionCheck(evalDir.resolve("frequency.tsv"), results, br -> String.format("%.1fMHz", 1000 / br.minPeriodMet), false);
            resultsTableExceptionCheck(evalDir.resolve("placerRuntime.tsv"), results, br -> {
                final List<BenchmarkResult.RuntimeLog> logLine = br.runtimes.stream().filter(r -> r.name.equals("Place Design")).collect(Collectors.toList());
                if (logLine.isEmpty()) {
                    return "?";
                }
                if (logLine.size()>1) {
                    return "multiple";
                }
                return String.format("%.3fs",logLine.get(0).runtime*1E-9);
            }, false);
            resultsTableExceptionCheck(evalDir.resolve("modulesInCriticalPath.tsv"), results, br -> {
                EvalData data = br.getEvalData(workDir);
                if (data == null) {
                    return "?!";
                }
                return String.valueOf(data.modulesInCriticalPath);
            }, false);
            resultsTableExceptionCheck(evalDir.resolve("numModules.tsv"), results, br -> {
                EvalData data = br.getEvalData(workDir);
                if (data == null) {
                    return "?!";
                }
                return data.numModuleInstances +"\t"+data.numModules;
            }, true);
            resultsTableExceptionCheck(evalDir.resolve("moduleSize.tsv"), results, br -> {
                EvalData data = br.getEvalData(workDir);
                if (data == null) {
                    return "?!";
                }


                final Stream<Map<String, Long>> mapStream = data.moduleData.values().stream().map(moduleData -> {
                    final Map<String, Long> collect = moduleData.entrySet().stream().collect(Collectors.groupingBy(t -> getType(t.getKey()), Collectors.summingLong(e -> e.getValue())));
                    collect.computeIfAbsent("BRAM", s->0L);
                    collect.computeIfAbsent("DSP", s->0L);
                    collect.computeIfAbsent("CLE", s->0L);
                    return collect;
                });
                final Map<String, LongSummaryStatistics> collect = mapStream
                        .flatMap(m -> m.entrySet().stream())
                        .collect(Collectors.groupingBy(e -> e.getKey(), Collectors.summarizingLong(e -> e.getValue())));
                return formatEntry("CLE", collect)+"\t"+formatEntry("BRAM", collect)+"\t"+formatEntry("DSP", collect);
            }, true);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String formatEntry(String name, Map<String, LongSummaryStatistics> collect) {
        final LongSummaryStatistics longSummaryStatistics = collect.get(name);
        if (longSummaryStatistics == null)
            return "0";
        if (longSummaryStatistics.getMin()== longSummaryStatistics.getMax()) {
            return ""+longSummaryStatistics.getMin();
        }
        return longSummaryStatistics.getMin()+"-"+ longSummaryStatistics.getMax()+" ("+ String.format("%.1f",longSummaryStatistics.getAverage())+")";
    }

    private static String getType(TileTypeEnum key) {
        if (key.toString().startsWith("CLE"))
            return "CLE";
        return key.toString();
    }

    private static void runLsf(Path workDir, Path cache, Stream<SomeRun> runs, boolean reuseMaxFreq, boolean evalOnly) throws IOException {
        //Check early :)
        FileTools.getVivadoPath();

        String strOpts = reuseMaxFreq ? " --reuseMaxFreq" : "";
        strOpts+= evalOnly ? " --evalOnly" : "";
        final String rapidWrightPath = FileTools.getRapidWrightPath();
        if (rapidWrightPath == null) {
            throw new RuntimeException("RAPIDWRIGHT_PATH not set");
        }
        String benchmarkCommand = rapidWrightPath +"/build/install/rapidwright/bin/rapidwright BenchmarkRunner"+ " --workdir "+workDir+" --cache "+cache+strOpts;
        Files.createDirectories(workDir);
        final Path runner = workDir.resolve("run.sh");
        if (!Files.exists(runner)) {
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(runner))) {
                pw.println("#!/bin/bash");
                pw.println(benchmarkCommand+" --lsf \"$@\"");
            }
            runner.toFile().setExecutable(true);
        }


        JobQueue queue = new JobQueue();
        runs
                .forEach(run -> {
                    final Job j = JobQueue.createJob();
                    j.setCommand(benchmarkCommand+" --run "+run.getRunArguments());
                    j.setRunDir(run.getJobDir(workDir).toString());
                    queue.addJob(j);

                });
        queue.runAllToCompletion();
    }

    private static void runLsf(Path workDir, Path cache, List<Benchmark> benchmarks, List<PlacerType> placers, boolean reuseMaxFreq, boolean evalOnly) throws IOException {
        runLsf(workDir, cache, getAllRuns(benchmarks, placers), reuseMaxFreq, evalOnly);
    }

    private static List<PlacerType> getPlacerList(PlacerType singlePlacer) {
        return Optional.ofNullable(singlePlacer)
                .map(Collections::singletonList)
                .orElseGet(()->Arrays.asList(PlacerType.values()));
    }

    private static List<Benchmark> getBenchmarkList(Benchmark singleBenchmark) {
        return Optional.ofNullable(singleBenchmark)
                .map(Collections::singletonList)
                .orElseGet(() -> BenchmarkFactory.getBenchmarkMap().values().stream().sorted(Comparator.comparing(Benchmark::getId)).collect(Collectors.toList()));
    }

    private static SomeRun findRun(Benchmark benchmark, PlacerType placer, String regularRun) {
        if (regularRun != null) {
            return findRegularRun(regularRun);
        }
        return new BenchmarkRun(benchmark, placer);
    }

    private static SomeRun findRegularRun(String regularRun) {
        final Stream<SomeRun> allRuns = getAllRuns(BenchmarkFactory.getBenchmarkMap().values(), Collections.singletonList(PlacerType.NewImpls));
        final List<SomeRun> collect = allRuns.filter(r -> r.getId().equals(regularRun) && r instanceof RegularRun).collect(Collectors.toList());
        if (collect.size() != 1) {
            throw new RuntimeException("Not exactly one run for "+ regularRun +": "+collect);
        }
        return collect.get(0);
    }


    private static void run(Path workDirRoot, Path realCache, SomeRun run, boolean reuseMaxFreq, boolean evalOnly) {
        Path workDir = run.getJobDir(workDirRoot);
        Path output = workDir.resolve("benchmark.json");

        BenchmarkResult result = new BenchmarkResult(run);
        CodePerfTracker t = new CodePerfTracker("Benchmark");
        try {
            //Check early :)
            FileTools.getVivadoPath();

            //Remove old output in case we crash
            if (Files.exists(output) && !evalOnly) {
                Files.delete(output);
            }


            try {
                Files.createDirectories(workDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            Pair<TimingResults, TimingResults> perf = run.doRun(t, workDirRoot, reuseMaxFreq, evalOnly);

            result.minPeriodMet = perf.getFirst().getMinPeriod();
            result.minPeriodNotMet = perf.getSecond().getMinPeriod();


        } catch (RuntimeException|IOException e) {
            e.printStackTrace();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(baos));
            result.exception = baos.toString();
        }
        t.stop();
        result.runtimes = BenchmarkResult.RuntimeLog.fromCodePerfTracker(t);
        t.printSummary();

        result.toJson(output);
    }

}
