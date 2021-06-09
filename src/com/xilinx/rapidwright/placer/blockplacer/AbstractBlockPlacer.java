package com.xilinx.rapidwright.placer.blockplacer;

import com.xilinx.rapidwright.design.AbstractModuleInst;

public abstract class AbstractBlockPlacer<ModuleInstT extends AbstractModuleInst<?,?>, PlacementT> {
    public abstract void setTempAnchorSite(ModuleInstT hm, PlacementT placement);
}
