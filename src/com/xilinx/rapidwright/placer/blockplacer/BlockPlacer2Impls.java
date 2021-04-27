package com.xilinx.rapidwright.placer.blockplacer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.AbstractModuleInst;
import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.ModulePlacement;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.TileRectangle;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFPortInst;

public class BlockPlacer2Impls extends BlockPlacer2<ModuleImplsInstance, ModulePlacement, ImplsPath> {

    private final List<ModuleImplsInstance> moduleInstances;
    private Map<Site, ModuleImplsInstance> currentAnchors = new HashMap<>();

    private final Map<ModuleImplsInstance, Set<ImplsPath>> modulesToPaths = new HashMap<>();

    public BlockPlacer2Impls(Design design, List<ModuleImplsInstance> moduleInstances) {
        super(design);
        this.moduleInstances = Collections.unmodifiableList(moduleInstances);
    }

    @Override
    public void setTempAnchorSite(ModuleImplsInstance hm, ModulePlacement placement) {
        placeHm(hm, placement);
    }

    @Override
    List<ModuleImplsInstance> getModuleImpls(boolean debugFlow) {
        return moduleInstances;
    }

    @Override
    Collection<ModulePlacement> getAllPlacements(ModuleImplsInstance hm) {
        return hm.getModule().getAllPlacements();
    }

    @Override
    void unsetTempAnchorSite(ModuleImplsInstance hm) {
        unplaceHm(hm);
    }

    @Override
    Comparator<ModulePlacement> getInitialPlacementComparator() {
        Tile center = dev.getTile(dev.getRows()/2, dev.getColumns()/2);
        return Comparator.comparingInt(i -> i.placement.getTile().getManhattanDistance(center));
    }

    @Override
    void placeHm(ModuleImplsInstance hm, ModulePlacement placement) {
        unplaceHm(hm);
        if (!currentAnchors.containsKey(placement.placement)) {
            currentAnchors.put(placement.placement, hm);
        }
        /*ModuleImplsInstance alreadyAtAnchor = currentAnchors.put(placement.placement, hm);
        if (alreadyAtAnchor != null && alreadyAtAnchor != hm) {
            throw new RuntimeException("Placing module "+hm.getName()+" at anchor "+placement.placement+", but "+alreadyAtAnchor.getName()+" is already there");
        }*/
        hm.place(placement);
    }

    @Override
    void unplaceHm(ModuleImplsInstance hm) {
        if (hm.getPlacement() != null) {
            currentAnchors.remove(hm.getPlacement().placement);
        }
        hm.unPlace();
    }

    private ImplsInstancePort toImplsInstancePort(EDIFPortInst portInst, Map<EDIFCellInst, Cell> edifToPhysical, Map<EDIFCellInst, ModuleImplsInstance> edifToModule) {
        if (portInst.getCellInst() != null) {
            EDIFCellInst cellInst = portInst.getCellInst();
            Cell cell = edifToPhysical.get(cellInst);
            ModuleImplsInstance module = edifToModule.get(cellInst);
            if (cell == null && module == null) {
                if (cellInst.getName().equals("VCC")) {
                    return null;
                }
                throw new RuntimeException("No physical representation of EDIF cellinst " + cellInst.getName());
            } else if (cell != null && module != null) {
                throw new RuntimeException("Duplicate representation of EDIF cellinst "+cellInst.getName());
            }

            if (cell != null) {
                List<String> siteWires = new ArrayList<>();
                SitePinInst spi = cell.getSitePinFromPortInst(portInst, siteWires);
                if (spi == null) {
                    /*System.out.println(siteWires);
                    System.out.println(cell.getAllSitePinsFromPortInst(portInst, siteWires));
                    System.out.println(siteWires);
                    System.out.println(cell.getSitePinFromPortInst(portInst, siteWires));
                    System.out.println(siteWires);*/
                    //TODO is this allowed to happen?
                    System.err.println("while creating an ImplsInstancePort for "+portInst+", could not find the port in cell "+cell);
                    return null;
                }
                return new ImplsInstancePort.SPI(spi);
            } else {
                return new ImplsInstancePort.InstPort(module, portInst.getName());
            }
        } else {
            return null; //TODO???
        }
    }

    @Override
    protected void populateAllPaths() {

        Map<EDIFCellInst, Cell> edifToPhysical = design.getCells().stream().collect(Collectors.toMap(Cell::getEDIFCellInst, Function.identity()));
        Map<EDIFCellInst, ModuleImplsInstance> edifToModule = moduleInstances.stream().collect(Collectors.toMap(AbstractModuleInst::getCellInst, Function.identity()));
        for (EDIFCellInst cellInst : design.getTopEDIFCell().getCellInsts()) {
            Cell cell = edifToPhysical.get(cellInst);
            ModuleImplsInstance module = edifToModule.get(cellInst);
            if (cell == null && module == null) {
                if (!cellInst.getName().equals("VCC")) {
                    throw new RuntimeException("No physical representation of EDIF cellinst " + cellInst.getName());
                }
            } else if (cell != null && module != null) {
                throw new RuntimeException("Duplicate representation of EDIF cellinst "+cellInst.getName());
            }
        }

        allPaths = new HashSet<>();
        for (EDIFNet net : design.getTopEDIFCell().getNets()) {
            ImplsPath path = new ImplsPath(net.getName());
            for (EDIFPortInst portInst : net.getPortInsts()) {
                ImplsInstancePort implsInstancePort = toImplsInstancePort(portInst, edifToPhysical, edifToModule);
                if (implsInstancePort != null) {
                    path.addPort(implsInstancePort);
                }
            }
            if (path.getSize() > 1) {
                allPaths.add(path);

                for (ImplsInstancePort port : path.ports) {
                    if (!(port instanceof ImplsInstancePort.InstPort)) {
                        continue;
                    }
                    ModuleImplsInstance instance = ((ImplsInstancePort.InstPort) port).getInstance();
                    modulesToPaths.computeIfAbsent(instance, x -> new HashSet<>()).add(path);
                }
            }
        }
    }

    @Override
    protected boolean checkValidPlacement(ModuleImplsInstance hm) {
        final boolean debugValidPlacement = false;
        if (hm.getPlacement() == null) {
            if (debugValidPlacement) System.out.println("not valid because "+hm.getName()+" is unplaced");
            return false;
        }
        for(ModuleImplsInstance other : hardMacros){
            if (other == hm) {
                continue;
            }
            if (other.getPlacement() == null) {
                continue;
            }
            if (hm.getPlacement().placement == other.getPlacement().placement) {
                if (debugValidPlacement) System.out.println("not valid because "+hm.getName()+" has same anchor as "+other.getName()+": "+hm.getPlacement().placement);

                return false;
            }
            if (hm.overlaps(other)){
                if (debugValidPlacement) System.out.println("not valid because "+hm.getName()+" overlaps "+other.getName());
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFinalPlacement() {

        //throw new RuntimeException("not implemented");
    }

    @Override
    protected ModulePlacement getTempAnchorSite(ModuleImplsInstance mi) {
        return mi.getPlacement();
    }

    @Override
    protected int getTileSize(ModuleImplsInstance hm) {
        if (hm.getCurrentModuleImplementation() == null) {
            return 0;
        }
        return hm.getCurrentModuleImplementation().getTileFootprintSize();
    }

    @Override
    protected boolean isInRange(ModulePlacement current, ModulePlacement newPlacement) {

        //TODO caching?
        TileRectangle rect = TileRectangle.fromSingleTile(current.placement.getTile()).expand((int) rangeLimit);
        return rect.isInside(newPlacement.placement.getTile());

    }

    @Override
    protected ModuleImplsInstance getHmCurrentlyAtPlacement(ModulePlacement placement) {
        return currentAnchors.get(placement.placement);
    }

    @Override
    protected ModulePlacement getCurrentPlacement(ModuleImplsInstance selected) {
        return selected.getPlacement();
    }

    @Override
    protected Collection<ImplsPath> getConnectedPaths(ModuleImplsInstance module) {
        return modulesToPaths.get(module);
    }

    @Override
    protected Tile getCurrentAnchorTile(ModuleImplsInstance mi) {
        return mi.getPlacement().placement.getTile();
    }

    private void dumpDot(java.nio.file.Path path) {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            new DotModuleImplsDumper(false).doDump(new DotModuleImplsDumper.ModuleImplsDumpData(design, moduleInstances, allPaths, modulesToPaths), pw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void initialPlacement() {
        super.initialPlacement();
        //dumpDot(Paths.get("/tmp/initial.dot"));
    }
}
