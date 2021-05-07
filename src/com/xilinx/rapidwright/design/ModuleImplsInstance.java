package com.xilinx.rapidwright.design;

import com.xilinx.rapidwright.edif.EDIFCellInst;

public class ModuleImplsInstance extends AbstractModuleInst<ModuleImplsInstance> {
    final ModuleImpls module;
    private ModulePlacement placement;

    public ModuleImplsInstance(String name, EDIFCellInst cellInst, ModuleImpls module) {
        super(name, cellInst);
        this.module = module;
    }

    public ModuleImplsInstance(String name, ModuleImpls module) {
        super(name);
        this.module = module;
    }

    public void unPlace() {
        placement = null;
    }

    public ModulePlacement getPlacement() {
        return placement;
    }

    public void place(ModulePlacement placement) {
        this.placement = placement;
        boundingBox = null;
    }

    public ModuleImpls getModule() {
        return module;
    }

    public Module getCurrentModuleImplementation() {
        if (placement == null) {
            return null;
        }
        return module.get(getPlacement().implementationIndex);
    }

    public boolean overlaps(ModuleImplsInstance other) {
        return getBoundingBox().overlaps(other.getBoundingBox());
    }


    private TileRectangle getBoundingBoxRecalc(){
        return getCurrentModuleImplementation().getSiteInsts().stream()
                .map(SiteInst::getTile)
                .map(tile-> getCurrentModuleImplementation().getCorrespondingTile(tile, placement.placement.getTile(), placement.placement.getDevice()))
                .collect(TileRectangle.collector()).orElseThrow(()->new RuntimeException("Routing only module"));
    }

    private TileRectangle getBoundingBoxEfficient() {
        return getCurrentModuleImplementation().getBoundingBox()
                .getCorresponding(placement.placement.getTile(), getCurrentModuleImplementation().getAnchor().getTile());
    }

    TileRectangle boundingBox = null;
    private TileRectangle getBoundingBox() {
        if (boundingBox == null) {
            TileRectangle efficient = getBoundingBoxEfficient();
        /*TileRectangle recalc = getBoundingBoxRecalc();
        if (!recalc.equals(efficient)) {
            throw new RuntimeException("differing bounding boxes for "+getName()+" at "+placement+": "+recalc+" vs "+efficient);
        }*/
            this.boundingBox = efficient;
        }
        return boundingBox;
    }
}

