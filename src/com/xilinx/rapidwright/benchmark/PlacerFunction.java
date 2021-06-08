package com.xilinx.rapidwright.benchmark;

import java.nio.file.Path;
import java.util.Collection;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImplsInstance;

@FunctionalInterface
public interface PlacerFunction {
    public void run(Design design, Collection<ModuleImplsInstance> instances, Path graphData);
}
