package com.xilinx.rapidwright.benchmark;

import java.nio.file.Path;
import java.util.Objects;

public abstract class RegularRun extends SomeRun {
    public abstract String getId();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegularRun that = (RegularRun) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public Path getJobDir(Path workDir) {
        return workDir.resolve(getId());
    }

    @Override
    public String getRunArguments() {
        return "--run --regular "+getId();
    }
}
