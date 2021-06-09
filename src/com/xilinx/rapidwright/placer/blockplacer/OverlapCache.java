package com.xilinx.rapidwright.placer.blockplacer;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.function.Predicate;

import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.RelocatableTileRectangle;
import com.xilinx.rapidwright.device.Device;

/**
 * Optimized Detection of overlaps.
 */
public class OverlapCache {
    private final Device device;
    private final Collection<ModuleImplsInstance> instances;
    private final Collection<ModuleImplsInstance>[][] modulesInArea;

    public static int DEFAULT_SIZE=23;

    private final int columnDivider;
    private final int rowDivider;

    private int getColumn(int fabricColumn) {
        return fabricColumn /columnDivider;
    }

    private int getRow(int fabricRow) {
        return fabricRow / rowDivider;
    }


    private int getColumns() {
        return getColumn(device.getColumns()-1)+1;
    }
    private int getRows() {
        return getRow(device.getRows()-1)+1;
    }

    private boolean allTouchedRegionsMatch(ModuleImplsInstance mii, Predicate<Collection<ModuleImplsInstance>> predicate) {
        final RelocatableTileRectangle bb = mii.getBoundingBox();

        final int crMinCol = getColumn(bb.getMinColumn());
        final int crMaxCol = getColumn(bb.getMaxColumn());
        final int crMinRow = getRow(bb.getMinRow());
        final int crMaxRow = getRow(bb.getMaxRow());

        for (int col = crMinCol; col <= crMaxCol; col++) {
            for (int row = crMinRow; row <= crMaxRow; row++) {
                boolean r = predicate.test(modulesInArea[col][row]);
                if (!r) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Remove an Instance from the cache. Has to be called before actually unplacing the instance
     * @param mii  the instance
     */
    public void unPlace(ModuleImplsInstance mii) {
        allTouchedRegionsMatch(mii,l->{l.remove(mii); return true;});
    }

    /**
     * Remove an Instance from the cache. Has to be called before after placing the instance
     * @param mii  the instance
     */
    public void place(ModuleImplsInstance mii) {
        allTouchedRegionsMatch(mii,l->{l.add(mii); return true;});
    }

    public OverlapCache(Device device, Collection<ModuleImplsInstance> instances, int size) {
        this.device = device;
        this.instances = instances;
        this.columnDivider = size;
        this.rowDivider = size;
        modulesInArea = new Collection[getColumns()][getRows()];
        for (int col = 0; col < modulesInArea.length; col++) {
            for (int row = 0; row < modulesInArea[col].length; row++) {
                modulesInArea[col][row] = new HashSet<>();
            }
        }
        for (ModuleImplsInstance instance : instances) {
            if (instance.getPlacement()!= null) {
                place(instance);
            }
        }
    }

    public boolean isValidPlacement(ModuleImplsInstance mii) {
        return allTouchedRegionsMatch(mii, l->{
            for (ModuleImplsInstance other : l) {
                if (other == mii) {
                    continue;
                }
                if (mii.getPlacement().placement == other.getPlacement().placement) {
                    return false;
                }
                if (mii.overlaps(other)){
                    return false;
                }
            }
            return true;
        });
    }

    private void checkCorrectness() {
        boolean error = false;
        for (int col = 0; col < modulesInArea.length; col++) {
            for (int row = 0; row < modulesInArea[col].length; row++) {
                Collection<ModuleImplsInstance> c = modulesInArea[col][row];
                for (ModuleImplsInstance moduleImplsInstance : c) {

                    if (moduleImplsInstance.getPlacement() == null) {
                        System.out.println(moduleImplsInstance+" is wrongly in "+col+"/"+row+", is not placed at all");
                        error = true;
                        continue;
                    }

                    final int minCol = getColumn(moduleImplsInstance.getBoundingBox().getMinColumn());
                    final int maxCol = getColumn(moduleImplsInstance.getBoundingBox().getMaxColumn());
                    final int minRow = getRow(moduleImplsInstance.getBoundingBox().getMinRow());
                    final int maxRow = getRow(moduleImplsInstance.getBoundingBox().getMaxRow());
                    boolean shouldBeIn = minCol <= col && col <= maxCol && minRow <= row && row <= maxRow;

                    if (!shouldBeIn) {
                        System.out.println(moduleImplsInstance+" is wrongly in "+col+"/"+row);
                        error = true;
                    }
                }

            }
        }

        for (ModuleImplsInstance moduleImplsInstance : instances) {
            for (ModuleImplsInstance other : instances) {
                if (other != moduleImplsInstance && other.overlaps(moduleImplsInstance)) {
                    System.out.println(moduleImplsInstance+" overlaps "+other);
                    error = true;
                }

            }

            if (moduleImplsInstance.getPlacement() == null) {
                continue;
            }

            final RelocatableTileRectangle bb = moduleImplsInstance.getBoundingBox();
            final int crMinCol = getColumn(bb.getMinColumn());
            final int crMaxCol = getColumn(bb.getMaxColumn());
            final int crMinRow = getRow(bb.getMinRow());
            final int crMaxRow = getRow(bb.getMaxRow());

            for (int col = crMinCol ; col <= crMaxCol; col++) {
                for (int row = crMinRow; row <= crMaxRow; row++) {
                    Collection<ModuleImplsInstance> c = modulesInArea[col][row];
                    if (!c.contains(moduleImplsInstance)) {
                        System.out.println(moduleImplsInstance+" should be in "+col+"/"+row);
                        error = true;
                    }
                }
            }
        }

        if (error) {
            throw new RuntimeException("error in overlaps");
        }
    }

    public void printStats() {
        checkCorrectness();
        System.out.println("Fabric: "+device.getColumns()+"x"+device.getRows());
        System.out.println("Regions: "+getColumns()+"x"+getRows());
        final IntSummaryStatistics instsPerArea = Arrays.stream(modulesInArea).flatMap(Arrays::stream)
                .mapToInt(Collection::size).summaryStatistics();
        System.out.println("Insts per Area: "+instsPerArea);
        final IntSummaryStatistics areasPerInst = instances.stream().mapToInt(inst -> {
            final int[] c = {0};
            allTouchedRegionsMatch(inst, l -> {
                c[0]++;
                return true;
            });
            return c[0];
        }).summaryStatistics();
        System.out.println("Areas per Inst: "+areasPerInst);

    }
}
