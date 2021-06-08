package com.xilinx.rapidwright.benchmark;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.Pair;

public abstract class Benchmark {
    private final String name;

    protected Benchmark(String name) {
        this.name = name;
    }

    public abstract String getType();
    public String getId() {
        return getType()+"/"+getName();
    }

    public String getName() {
        return name;
    }

    public abstract Pair<Design, Collection<ModuleImplsInstance>> createModulesDesign(CodePerfTracker t, Path cache);
    public abstract Optional<String> getUnavailableReason();

    public abstract double getEstimatedMaxFreqMhz();

    public abstract RegularRun getRegularRun();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Benchmark benchmark = (Benchmark) o;
        return getId().equals(benchmark.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    public abstract String getRunGroup();

    public abstract String getClockName();

    public abstract Pair<Integer,Integer> getModuleCounts();

    public abstract Stream<Pair<Path, Path>> getModuleDcps(Path workDirRoot);
}
