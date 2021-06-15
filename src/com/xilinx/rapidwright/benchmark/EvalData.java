package com.xilinx.rapidwright.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.device.TileTypeEnum;
import com.xilinx.rapidwright.util.Pair;

public class EvalData {
    public final int numModules;
    public final int numModuleInstances;
    public final Map<Path, Map<TileTypeEnum, Long>> moduleData;
    public final int modulesInCriticalPath;

    public EvalData(int numModules, int numModuleInstances, Map<Path, Map<TileTypeEnum, Long>> moduleData, int modulesInCriticalPath) {
        this.numModules = numModules;
        this.numModuleInstances = numModuleInstances;
        this.moduleData = moduleData;
        this.modulesInCriticalPath = modulesInCriticalPath;
    }

    private static <T> void toTsv(Map<T,Long> map, Path p) {
        final List<String> lines = map.keySet().stream().map(k -> k + "\t" + map.get(k)).collect(Collectors.toList());
        try {
            Files.write(p, lines);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> Map<T, Long> fromTsv(Path p, Function<String, T> valueReader) {
        try (Stream<String> lines = Files.lines(p)){
            return lines
                    .map(line -> {
                        final String[] split = line.split("\t");
                        if (split.length!=2) {
                            throw new RuntimeException("invalid line: "+line);
                        }
                        return split;
                    }).collect(Collectors.toMap(split->valueReader.apply(split[0]), split->Long.parseLong(split[1])));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static <T> Map<T,Long> calcOrRead(Path p, Supplier<Map<T,Long>> supplier, Function<String, T> valueReader) {
        if (Files.exists(p)) {
            return fromTsv(p, valueReader);
        }
        Map<T,Long> res = supplier.get();
        toTsv(res, p);
        return res;
    }

    public static EvalData fromRunDir(Path parent, SomeRun run, Path workDirRoot, BenchmarkResult benchmarkResult) {
        if (!(run instanceof BenchmarkRun)) {
            return null;
        }

        final Benchmark benchmark = ((BenchmarkRun) run).benchmark;
        final Pair<Integer, Integer> moduleCounts = benchmark.getModuleCounts();
        Map<Path, Map<TileTypeEnum, Long>> moduleData = analyzeModules(workDirRoot, benchmark);


        int modulesInCriticalPath = getCriticalPathModuleCount(parent, benchmarkResult);

        return new EvalData(moduleCounts.getFirst(), moduleCounts.getSecond(), moduleData, modulesInCriticalPath);
    }

    private static double freqFromDirName(Path p) {
        return Double.parseDouble(p.getFileName().toString().replace("route_",""));
    }

    enum TiminDataState {
        BEFORE,
        TIMING_PRE,
        PRE_CLOCK,
        IN_CLOCK,
        INSIDE,
        AFTER
    }
    private static int getCriticalPathModuleCount(Path parent, BenchmarkResult benchmarkResult) {

        try {
            Set<String> modules = new HashSet<>();
            final Path maxFreq = parent.resolve("maxFreq");
            if (!Files.exists(maxFreq)) {
                return -1;
            }
            final Path dir;
            try (Stream<Path> list = Files.list(maxFreq)) {
                dir = list
                        .filter(Files::isDirectory).min(Comparator.comparing(d -> Math.abs(freqFromDirName(d) - benchmarkResult.minPeriodMet)))
                        .orElseThrow(() -> new RuntimeException("noting found"));
            }

            final List<String> lines = Files.readAllLines(dir.resolve("timing_summary.txt"));
            List<String> content = new ArrayList<>();
            TiminDataState state = TiminDataState.BEFORE;
            String pre_line = null;
            for (String line : lines) {

                switch (state) {
                    case BEFORE:
                        if (line.trim().equals("Max Delay Paths")) {
                            state = TiminDataState.TIMING_PRE;
                        }
                        break;
                    case TIMING_PRE:
                        if (line.trim().startsWith("Location")) {
                            state = TiminDataState.PRE_CLOCK;
                        }
                        break;
                    case PRE_CLOCK:
                        state = TiminDataState.IN_CLOCK;
                        break;
                    case IN_CLOCK:
                        if (line.contains("-----")) {
                            pre_line = line;
                            state = TiminDataState.INSIDE;
                        }
                        break;
                    case INSIDE:
                        if (line.contains("-----")) {
                            state = TiminDataState.AFTER;
                        } else {
                            content.add(line);
                        }
                        break;
                    default:
                        //Nothing to do
                }

            }
            if (pre_line == null ||content.isEmpty()) {
                throw new RuntimeException("invalid file");
            }
            final int separator = pre_line.indexOf("----    ");
            if (separator<0) {
                throw new RuntimeException("invalid pre-line: "+pre_line);
            }
            int offset = separator +8;
            for (String s : content) {
                if (s.length()<offset+15) {
                    continue;
                }
                final String instance = s.substring(offset);
                final String[] split = instance.split("/");
                if (split.length<2) {
                    throw new RuntimeException("invalid instance: "+s);
                }
                modules.add(split[0]);
            }
            return modules.size();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static Map<Path, Map<TileTypeEnum, Long>> analyzeModules(Path workDirRoot, Benchmark benchmark) {
        final List<Pair<Path, Path>> dcps = benchmark.getModuleDcps(workDirRoot).collect(Collectors.toList());
        Map<Path, Map<TileTypeEnum, Long>> res = new HashMap<>();
        for (Pair<Path,Path> pair : dcps) {
            Path dcp = pair.getFirst();
            Path edf = pair.getSecond();
            if (!Files.exists(dcp)) {
                throw new RuntimeException("does not exist: "+dcp);
            }
            final Supplier<Design> design = ()->Design.readCheckpoint(dcp, edf);
            final Path siteInstPath = dcp.resolveSibling(dcp.getFileName().toString() + "_siteInsts.tsv");
            final Path tilePath = dcp.resolveSibling(dcp.getFileName().toString() + "_tiles.tsv");
            final Map<SiteTypeEnum, Long> siteInstTypes = calcOrRead(
                    siteInstPath,
                    ()->design.get().getSiteInsts().stream().collect(Collectors.groupingBy(SiteInst::getSiteTypeEnum, Collectors.counting())),
                    SiteTypeEnum::valueOf
            );
            final Map<TileTypeEnum, Long> tileTypes = calcOrRead(
                    tilePath,
                    ()->design.get().getSiteInsts().stream().map(SiteInst::getTile).distinct().collect(Collectors.groupingBy(Tile::getTileTypeEnum, Collectors.counting())),
                    TileTypeEnum::valueOf
            );
            res.put(dcp, tileTypes);

        }
        return res;
    }

    @Override
    public String toString() {
        return "EvalData{" +
                "numModules=" + numModules +
                ", numModuleInstances=" + numModuleInstances +
                '}';
    }
}
