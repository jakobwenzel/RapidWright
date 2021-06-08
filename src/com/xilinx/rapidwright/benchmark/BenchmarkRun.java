package com.xilinx.rapidwright.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.edif.EDIFPort;
import com.xilinx.rapidwright.placer.blockplacer.BlockPlacer2Impls;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.util.performance_evaluation.PerformanceEvaluation;
import com.xilinx.rapidwright.util.performance_evaluation.TimingResults;

public class BenchmarkRun extends SomeRun{

    public final Benchmark benchmark;
    public final PlacerType placer;

    public BenchmarkRun(Benchmark benchmark, PlacerType placer) {
        this.benchmark = benchmark;
        this.placer = placer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BenchmarkRun that = (BenchmarkRun) o;
        return benchmark.equals(that.benchmark) && placer == that.placer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(benchmark, placer);
    }

    @Override
    public Path getJobDir(Path workDirRoot) {
        return workDirRoot.resolve(benchmark.getId()).resolve(placer.toString());
    }

    @Override
    public String getRunArguments() {
        return "--benchmark "+benchmark.getId()+" --placer "+placer;
    }

    @Override
    public String getId() {
        return benchmark.getId()+"/"+placer;
    }

    @Override
    public String getRunGroup() {
        return benchmark.getRunGroup();
    }

    public static String guessClockName(Design design) {
        List<EDIFPort> list = design.getTopEDIFCell().getPorts().stream()
                .filter(p -> (p.getName().contains("clk") || p.getName().contains("clock")) && !p.getName().contains("out"))
                .collect(Collectors.toList());
        if (list.size() != 1) {
            if (list.size() == 0) {
                return "ap_clk";
            }
            //throw new RuntimeException("Did not find one clock port but: "+list);
        }
        return list.get(0).getName();
    }


    private static void toModules(Design design, Collection<ModuleImplsInstance> instances, CodePerfTracker t) {
        t.stop().start("Convert to Modules");
        BlockPlacer2Impls dummyPlacer = new BlockPlacer2Impls(design, instances, false, null);
        dummyPlacer.initializePlacer(false);
        DesignTools.createModuleInstsFromModuleImplsInsts(design, instances, dummyPlacer.getPaths());
    }

    @Override
    public Pair<TimingResults, TimingResults> doRun(CodePerfTracker t, Path workDirRoot, boolean reuseMaxFreq, boolean evalOnly) {
        Path workDir = getJobDir(workDirRoot);

        final Path checkpoint = workDir.resolve("placed.dcp");
        if (!evalOnly) {

            //TODO use normal cache once we have locking
            Path cache = workDirRoot.resolve(benchmark.getId()).resolve("cache");
            try {
                Files.createDirectories(cache);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Pair<Design, Collection<ModuleImplsInstance>> pair = benchmark.createModulesDesign(t, cache);
            Design design = pair.getFirst();
            Collection<ModuleImplsInstance> instances = pair.getSecond();
            if (placer.runsOnModules()) {
                toModules(design, instances, t);
            }

            t.stop().start("Place Design");
            placer.place(design, instances, workDir.resolve("place.tsv"));


            if (!placer.runsOnModules()) {
                toModules(design, instances, t);
            }

            t.stop().start("Write Checkpoint");
            design.writeCheckpoint(checkpoint);
        }
        t.stop().start("Evaluate Performance");
        //return new Pair<>(new TimingResults(0,0,true, 0), new TimingResults(0,0,false, 0));
        return PerformanceEvaluation.getMaxFrequency(checkpoint, benchmark.getClockName(), workDir.resolve("maxFreq"), reuseMaxFreq, benchmark.getEstimatedMaxFreqMhz());
    }
}
