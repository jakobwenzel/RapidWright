package com.xilinx.rapidwright.benchmark;

import java.nio.file.Path;

import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.util.performance_evaluation.TimingResults;

public abstract class SomeRun implements Comparable<SomeRun>{
    public abstract Path getJobDir(Path workDir);
    public abstract String getRunArguments();

    @Override
    public int compareTo(SomeRun o) {
        return getId().compareTo(o.getId());
    }

    public abstract String getId();
    public abstract String getRunGroup();
    public abstract Pair<TimingResults, TimingResults> doRun(CodePerfTracker t, Path workDirRoot, boolean reuseMaxFreq, boolean evalOnly);
}
