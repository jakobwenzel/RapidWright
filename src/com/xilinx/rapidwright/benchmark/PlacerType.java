package com.xilinx.rapidwright.benchmark;

import java.nio.file.Path;
import java.util.Collection;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.oldPlacer.blockplacer.OldBlockPlacer2;
import com.xilinx.rapidwright.placer.blockplacer.BlockPlacer2Impls;
import com.xilinx.rapidwright.placer.blockplacer.BlockPlacer2Module;
import joptsimple.util.EnumConverter;

public enum PlacerType {
    Old(true, (d,i, graphData)->new OldBlockPlacer2().placeDesign(d, false)),
    NewModules(true, (d,i, graphData)->new BlockPlacer2Module(d, false, graphData).placeDesign(false)),
    NewImpls(false, (d,i, graphData)->new BlockPlacer2Impls(d, i, false, graphData).placeDesign(false)),
    NewModulesIgnoreClk(true, (d,i, graphData)->new BlockPlacer2Module(d, true, graphData).placeDesign(false)),
    NewImplsIgnoreClk(false, (d,i, graphData)->new BlockPlacer2Impls(d, i, true, graphData).placeDesign(false));

    private final boolean runsOnModules;
    private final PlacerFunction placer;

    PlacerType(boolean runsOnModules, PlacerFunction placer) {
        this.runsOnModules = runsOnModules;
        this.placer = placer;
    }

    public static EnumConverter<PlacerType> createConverter() {
        return new EnumConverter<PlacerType>(PlacerType.class) {
        };
    }

    public boolean runsOnModules() {
        return runsOnModules;
    }

    public void place(Design design, Collection<ModuleImplsInstance> instances, Path graphData) {
        placer.run(design, instances, graphData);
    }
}
