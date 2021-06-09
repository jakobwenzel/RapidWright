package com.xilinx.rapidwright.design;

import java.util.Map;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.placer.blockplacer.ImplsInstancePort;

public class ModuleImplsInstance extends AbstractModuleInst<ModuleImpls, ModuleImplsInstance> {
    final ModuleImpls module;
    private ModulePlacement placement;

    private final Map<String, ImplsInstancePort.InstPort> ports;


    public ModuleImplsInstance(String name, EDIFCellInst cellInst, ModuleImpls module) {
        super(name, cellInst);
        this.module = module;
        ports = module.get(0).getPorts().stream()
                .collect(Collectors.toMap(Port::getName, p->new ImplsInstancePort.InstPort(this, p.getName())));
    }

    public ModuleImplsInstance(String name, ModuleImpls module) {
        this(name, null, module);
    }

    public void unPlace() {
        placement = null;
        boundingBox = null;
        for (ImplsInstancePort.InstPort port : ports.values()) {
            port.resetBoundingBox();
        }
    }

    public ModulePlacement getPlacement() {
        return placement;
    }

    public void place(ModulePlacement placement) {
        unPlace();
        this.placement = placement;
    }

    @Override
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


    private RelocatableTileRectangle getBoundingBoxRecalc(){
        return getCurrentModuleImplementation().getSiteInsts().stream()
                .map(SiteInst::getTile)
                .map(tile-> {
                    final Tile correspondingTile = getCurrentModuleImplementation().getCorrespondingTile(tile, placement.placement.getTile());
                    if (correspondingTile == null) {
                        if (!getCurrentModuleImplementation().getAllValidPlacements().contains(placement.placement)) {
                            throw new RuntimeException("Module "+module+" is at invalid placement "+placement+", not supported by module");
                        }
                        throw new NullPointerException("No corresponding tile for "+tile+" of module "+module.getName()+" at "+placement.placement+", "+placement.placement.getTile());
                    }
                    return correspondingTile;
                })
                .collect(RelocatableTileRectangle.collector());
    }


    private RelocatableTileRectangle getBoundingBoxEfficient() {
        return ((RelocatableTileRectangle) getCurrentModuleImplementation().getBoundingBox())
                .getCorresponding(placement.placement.getTile(), getCurrentModuleImplementation().getAnchor().getTile());
    }

    private void tryPinpointError() {
        getCurrentModuleImplementation().getSiteInsts().stream()
                .map(SiteInst::getTile)
                .distinct()
                .forEach(originalTile -> {
                    TileRectangle moveRect = RelocatableTileRectangle.fromSingleTile(originalTile).getCorresponding(placement.placement.getTile(), getCurrentModuleImplementation().getAnchor().getTile());
                    TileRectangle moveTile = RelocatableTileRectangle.fromSingleTile(getCurrentModuleImplementation().getCorrespondingTile(originalTile, placement.placement.getTile()));
                    if (!moveRect.equals(moveTile)) {
                        System.out.println("originalTile = " + originalTile);
                        System.out.println("moveRect = " + moveRect);
                        System.out.println("moveTile = " + moveTile);
                        System.out.println("getCurrentModuleImplementation().getAnchor().getTile() = " + getCurrentModuleImplementation().getAnchor().getTile());
                        System.out.println("getCurrentModuleImplementation().getAnchor() = " + getCurrentModuleImplementation().getAnchor());
                        System.out.println("placement = " + placement.placement);
                        System.out.println("placement.getTile() = " + placement.placement.getTile());
                        throw new RuntimeException("Found weird tile");
                    }
                });
    }

    RelocatableTileRectangle boundingBox = null;
    public RelocatableTileRectangle getBoundingBox() {
        if (boundingBox == null) {
            RelocatableTileRectangle efficient = getBoundingBoxEfficient();
            /*TileRectangle recalc = getBoundingBoxRecalc();
            if (!recalc.equals(efficient)) {
                tryPinpointError();
                throw new RuntimeException("differing bounding boxes for " + getName() + " at " + placement + ": " + recalc + " vs " + efficient);
            }*/
            this.boundingBox = efficient;
        }
        return boundingBox;
    }

    public ImplsInstancePort getPort(String name) {
        final ImplsInstancePort.InstPort instPort = ports.get(name);
        if (instPort == null) {
            throw new RuntimeException("Invalid port for "+module+": "+name);
        }
        return instPort;

    }
}

