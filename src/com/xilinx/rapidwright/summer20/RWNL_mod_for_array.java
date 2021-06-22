package com.xilinx.rapidwright.summer20;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.device.Site;

import java.util.Iterator;

public class RWNL_mod_for_array {

    public static void main(String[] args) {
        Design design;
        if (args.length == 1 && args[0].compareTo("opt") == 0)
            design = Design.readCheckpoint("USS_opt_u250.dcp");
        else
            design = Design.readCheckpoint("shell.dcp");

        Module pattern_1_1 = new Module(Design.readCheckpoint("tile_p1_1.dcp"));
        Module pattern_1_2 = new Module(Design.readCheckpoint("tile_p1_2.dcp"));
        Module pattern_1_3 = new Module(Design.readCheckpoint("tile_p1_3.dcp"));
        Module pattern_1_4 = new Module(Design.readCheckpoint("tile_p1_4.dcp"));
        Module pattern_1_5 = new Module(Design.readCheckpoint("tile_p1_5.dcp"));
        Module pattern_1_6 = new Module(Design.readCheckpoint("tile_p1_6.dcp"));

        Module pattern_2_1 = new Module(Design.readCheckpoint("tile_p2_1.dcp"));
        Module pattern_2_2 = new Module(Design.readCheckpoint("tile_p2_2.dcp"));
        Module pattern_2_3 = new Module(Design.readCheckpoint("tile_p2_3.dcp"));
        Module pattern_2_4 = new Module(Design.readCheckpoint("tile_p2_4.dcp"));
        Module pattern_2_5 = new Module(Design.readCheckpoint("tile_p2_5.dcp"));
        Module pattern_2_6 = new Module(Design.readCheckpoint("tile_p2_6.dcp"));

        Module pattern_3_1 = new Module(Design.readCheckpoint("tile_p3_1.dcp"));
        Module pattern_3_2 = new Module(Design.readCheckpoint("tile_p3_2.dcp"));
        Module pattern_3_3 = new Module(Design.readCheckpoint("tile_p3_3.dcp"));
        Module pattern_3_4 = new Module(Design.readCheckpoint("tile_p3_4.dcp"));
        Module pattern_3_5 = new Module(Design.readCheckpoint("tile_p3_5.dcp"));
        Module pattern_3_6 = new Module(Design.readCheckpoint("tile_p3_6.dcp"));

        Module pattern_4_1 = new Module(Design.readCheckpoint("tile_p4_1.dcp"));
        Module pattern_4_2 = new Module(Design.readCheckpoint("tile_p4_2.dcp"));
        Module pattern_4_3 = new Module(Design.readCheckpoint("tile_p4_3.dcp"));
        Module pattern_4_4 = new Module(Design.readCheckpoint("tile_p4_4.dcp"));
        Module pattern_4_5 = new Module(Design.readCheckpoint("tile_p4_5.dcp"));
        Module pattern_4_6 = new Module(Design.readCheckpoint("tile_p4_6.dcp"));


        Module[][] myModules = new Module[][] {
                {pattern_1_2, pattern_1_3, pattern_1_4, pattern_1_6},
                {pattern_2_2, pattern_2_3, pattern_2_4, pattern_2_6},
                {pattern_3_2, pattern_3_3, pattern_3_4, pattern_3_6},
                {pattern_4_2, pattern_4_3, pattern_4_4, pattern_4_6}
        };
        Site[][] myAnchors = new Site[myModules.length][myModules[0].length];

        for(int j = 0; j < myModules.length; j++) {
            for (int i = 0; i < myModules[0].length; i++) {
                Module myModule = myModules[j][i];
                design.addModule(myModule);
                design.getNetlist().migrateCellAndSubCells(myModule.getNetlist().getTopCell(), true);

                System.out.println("Site of anchor of module " + myModule + ":");
                Site anchor = myModule.getAnchor().getSite();
                System.out.println(anchor);
                myAnchors[j][i] = anchor;
            }
        }

        // Create and place instances of tile modules on user shell
        long startTime = System.nanoTime();

        String[][] tileInsts = new String[][]{
                {"tile1_1", "tile1_2", "tile1_3", "tile1_4"},
                {"tile2_1", "tile2_2", "tile2_3", "tile2_4"},
                {"tile3_1", "tile3_2", "tile3_3", "tile3_4"},
                {"tile4_1", "tile4_2", "tile4_3", "tile4_4"}
        };
        for(int j = 0; j < myModules.length; j++) {
            for (int i = 0; i < myModules[0].length; i++) {

            int position = 0;//90; //120;
                System.out.println("tileInst:"+tileInsts[j][i]);
                DesignTools.makeBlackBox(design, tileInsts[j][i]);
                ModuleInst mi = design.createModuleInst(tileInsts[j][i], myModules[j][i]);
                if (!mi.place(myAnchors[j][i].getNeighborSite(0, position))) {
                    throw new RuntimeException("ERROR: Failed to place module " + tileInsts[j][i]);
                }
            }
        }


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

}
