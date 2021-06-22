package com.xilinx.rapidwright.summer20;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.device.Site;

import java.util.Iterator;

public class RWNL_mod_for_open_nic {

    public static void main(String[] args) {
        Design design = Design.readCheckpoint("open_nic_opt_bb.dcp");

        //Module pattern1 = new Module(Design.readCheckpoint("u250_box_with_shell_and_tiles_routed.dcp"));
        Module pattern1 = new Module(Design.readCheckpoint("box_with_shell_and_tiles.dcp"));


        Module[] myModules = new Module[] {pattern1};
        Site[] myAnchors = new Site[myModules.length];

        Module myModule = myModules[0];
        design.addModule(myModule);
        design.getNetlist().migrateCellAndSubCells(myModule.getNetlist().getTopCell(), true);

        System.out.println("Site of anchor of module " + myModule  + ":");
        Site anchor = myModule.getAnchor().getSite();
        System.out.println(anchor);
        myAnchors[0] = anchor;

        // Create and place instances of tile modules on user shell
        long startTime = System.nanoTime();        

        String boxInstName = "box_250mhz_inst";
            int position = 240; // 0
        System.out.println("boxInst:"+boxInstName);
        DesignTools.makeBlackBox(design, boxInstName);
        ModuleInst mi = design.createModuleInst(boxInstName, myModules[0]);
        if (!mi.place(myAnchors[0].getNeighborSite(0, position))) {
            throw new RuntimeException("ERROR: Failed to place module " + boxInstName);
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


        //design.getNet("axil_aclk_IBUF_BUFG").unroute();
        //design.getNet("axis_aclk_IBUF_BUFG").unroute();
        design.flattenDesign();
        design.writeCheckpoint("open_nic_box_with_shell_and_tiles.dcp");

    }

}
