package com.xilinx.rapidwright.summer20;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.RelocatableTileRectangle;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.TileRectangle;
import com.xilinx.rapidwright.device.Site;

public class RWNL_mod_for_4x4 {

    public static void main(String[] args) {
        Design design;
        if (args.length == 1 && args[0].compareTo("opt") == 0)
            design = Design.readCheckpoint("USS_opt_u250.dcp");
        else
            design = Design.readCheckpoint("shell.dcp");


        Module pattern1 = new Module(Design.readCheckpoint("tile_p1.dcp"));
        pattern1.setName("pattern1");
        Module pattern2 = new Module(Design.readCheckpoint("tile_p2.dcp"));
        pattern2.setName("pattern2");
        Module pattern3 = new Module(Design.readCheckpoint("tile_p3.dcp"));
        pattern3.setName("pattern3");
        Module pattern4 = new Module(Design.readCheckpoint("tile_p4.dcp"));
        pattern4.setName("pattern4");

        Module[] myModules = new Module[] {pattern1, pattern2, pattern3, pattern4};
        Site[] myAnchors = new Site[myModules.length];

        for(int i = 0; i < myModules.length; i++) {
            Module myModule = myModules[i];
            design.addModule(myModule);
            design.getNetlist().migrateCellAndSubCells(myModule.getNetlist().getTopCell(), true);

            System.out.println("Site of anchor of module " + myModule  + ":");
            Site anchor = myModule.getAnchor().getSite();
            System.out.println(anchor);
            myAnchors[i] = anchor;
        }

        // Create and place instances of tile modules on user shell
        long startTime = System.nanoTime();        

        for(int i = 0; i < myModules.length; i++) {
            String id = Integer.toString(i + 1);

            String[] tileInsts = new String[]{"tile1_" + id, "tile2_" + id, "tile3_" + id, "tile4_" + id};
            int position = 90;//30; // 0
            for (String tileInstName : tileInsts) {
                System.out.println("tileInst:"+tileInstName);
                DesignTools.makeBlackBox(design, tileInstName);
                ModuleInst mi = design.createModuleInst(tileInstName, myModules[i]);
                if (!mi.place(myAnchors[i].getNeighborSite(0, position))) {
                    throw new RuntimeException("ERROR: Failed to place module " + tileInstName);
                }
                position = position - 30; // - 60
            }

        }
        unrouteCrossArrayNets(design);


        if (true) {


            long endTime = System.nanoTime();
            long timeElapsed = endTime - startTime;
            System.out.println("Stitch execution time in seconds: " + timeElapsed / 1000000000);

            design.getNet(Net.VCC_NET).unroute();
            design.getNet(Net.GND_NET).unroute();

            //try {
            //   design.routeSites();
            //} catch (NullPointerException npe) {
            //    System.out.println("Caught npe:" + npe);
            //    npe.printStackTrace();
            //}

            design.getNetlist().resetParentNetMap();
            {  // code snippet taken from design.routeSites() to try to debug this in block below
                Iterator var1 = design.getSiteInsts().iterator();

                while(var1.hasNext()) {
                    try {
                        if (var1.hasNext())
                            ((SiteInst) var1.next()).routeSite();
                    } catch (NullPointerException npe) {
                        System.out.println("Caught npe: "+npe);
                    }
                    catch (RuntimeException rte) {
                        System.out.println("Caught rte: "+rte);
                    }
                }

            }

            long endTime2 = System.nanoTime();
            timeElapsed = endTime2 - endTime;
            System.out.println("Site router execution time in seconds: " + timeElapsed/1000000000);

        }


        //design.getNet("clk_line_IBUF_BUFG").unroute();
        //design.getNet("clk_control_IBUF_BUFG").unroute();

        design.writeCheckpoint("tiles_and_shell.dcp");

    }

    private static void incCounter(Map<String, Integer> counters, String name) {
        int existing = counters.getOrDefault(name, 0);
        counters.put(name, existing+1);
    }

    private static void unrouteCrossArrayNets(Design design) {

        TreeMap<String, Integer> namedCounters = new TreeMap<>();

        final RelocatableTileRectangle arrayRect = design.getModuleInsts().stream().map(ModuleInst::getBoundingBox).reduce(new RelocatableTileRectangle(), (a, b) -> {
            a.extendTo(b);
            return a;
        });

        for (Net net : design.getNets()) {
            if (net.getName().startsWith("clk")) {
                //Don't change clocks
                continue;
            }
            if (net.getPins().isEmpty()) {
                continue;
            }
            final TileRectangle netRectangle = net.getPins().stream().map(SitePinInst::getTile).collect(TileRectangle.collector());


            boolean hasModulePin = net.getPins().stream().anyMatch(pin->pin.getSiteInst().getModuleInst() != null);
            boolean hasShellPin = net.getPins().stream().anyMatch(pin->pin.getSiteInst().getModuleInst() == null);
            boolean hasRouting = !net.getPIPs().isEmpty();

            if (hasModulePin && hasShellPin) {
                if (hasRouting) {
                    incCounter(namedCounters, "shell->module routed");
                } else {
                    incCounter(namedCounters, "shell->module unrouted");
                }
            } else if (hasShellPin) {
                if (hasRouting) {
                    //incCounter(namedCounters, "shell routed");
                    if (netRectangle.overlaps(arrayRect)) {
                        incCounter(namedCounters, "shell routed overlapped");

                        System.out.println("unrouting "+net.getName());
                        net.unroute();
                        net.unlockRouting();
                    } else {
                        incCounter(namedCounters, "shell routed outside");
                    }
                } else {
                    incCounter(namedCounters, "shell unrouted");
                }

            } else {
                if (hasRouting) {
                    incCounter(namedCounters, "module routed");
                } else {
                    incCounter(namedCounters, "module unrouted");
                }
            }

        }
        namedCounters.forEach((name, value) -> {
            System.out.println(name+": "+value);
        });
    }

}
