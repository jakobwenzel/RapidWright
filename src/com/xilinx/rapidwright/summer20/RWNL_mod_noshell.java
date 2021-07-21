package com.xilinx.rapidwright.summer20;

import java.nio.file.Paths;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.SiteInst;

public class RWNL_mod_noshell {
    static boolean keepMod(int x, int y) {
        if (x == 12 || x == 15) {
            return y==2 || y==4;
        }
        return true;
    }
    public static void main(String[] args) {
        Module[] modules = RWNL_mod_for_4x4.loadModules(Paths.get(args[0]));

        Design design = new Design("design", "xcu250");
        for (Module myModule : modules) {
            design.addModule(myModule);
            design.getNetlist().migrateCellAndSubCells(myModule.getNetlist().getTopCell(), true);

        }
        for (Module m: modules) {
            for (int y = 1; y<=4; y++) {
                final String name = "tile" + y + "_" + m.getName();
                if (!keepMod(Integer.parseInt(m.getName()), y)) {
                    System.out.println("discarding "+name);
                    continue;
                }
                System.out.println("creating "+name);
                final ModuleInst inst = design.createModuleInst(name, m);
                int yDiff = 120 - 30*y;
                if (!inst.place(m.getAnchor().getSite().getNeighborSite(0, yDiff))) {
                    System.err.println("failed to place "+inst);
                }
            }
        }

        final String errors = design.getSiteInsts().stream().filter(SiteInst::isPlaced).collect(Collectors.groupingBy(SiteInst::getSite))
                .values()
                .stream().filter(v -> v.size() > 1)
                .map(v -> "Placed at " + v.get(0).getSite() + ": " + v)
                .collect(Collectors.joining("\n"));
        if (!errors.isEmpty()) {
            throw new RuntimeException(errors);
        }

        //AnalyzeConflictNets.analyzeOverlapping(design);
        //HandPlacer.openDesign(design);
        design.writeCheckpoint("onlymodules.dcp");
    }
}
