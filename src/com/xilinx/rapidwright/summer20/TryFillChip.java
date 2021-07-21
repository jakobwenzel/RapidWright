package com.xilinx.rapidwright.summer20;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QColor;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.RelocatableTileRectangle;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.examples.TileWindow;

public class TryFillChip {
    public static void main(String[] args) {

        Path patternDir = Paths.get(args[0]);
        if (!Files.isDirectory(patternDir)) {
            throw new RuntimeException("not a dir at "+patternDir);
        }

        Module pattern1 = new Module(Design.readCheckpoint(patternDir.resolve("tile_p1.dcp")));
        pattern1.setName("pattern1");
        Module pattern2 = new Module(Design.readCheckpoint(patternDir.resolve("tile_p2.dcp")));
        pattern2.setName("pattern2");
        Module pattern3 = new Module(Design.readCheckpoint(patternDir.resolve("tile_p3.dcp")));
        pattern3.setName("pattern3");
        Module pattern4 = new Module(Design.readCheckpoint(patternDir.resolve("tile_p4.dcp")));
        pattern4.setName("pattern4");

        Module[] myModules = new Module[] {pattern1, pattern2, pattern3, pattern4};


        Design design = new Design("dummy", pattern1.getDevice().getName());


        List<UiPBlock> rects = new ArrayList<>();
        for(int x = 0; x < myModules.length; x++) {
            String id = Integer.toString(x + 1);


            final int yDiff = 30;

            final Site origAnchor = myModules[x].getAnchor().getSite();
            Site anchor = origAnchor;
            Site upMoveAnchor;
            while ((upMoveAnchor = anchor.getNeighborSite(0,yDiff)) != null) {
                anchor = upMoveAnchor;
            }

            int y = 0;
            while (anchor != null) {
                y++;
                String tileInstName = "tile"+y+"_"+id;

                System.out.println("tileInst:"+tileInstName);
                ModuleInst mi = design.createModuleInst(tileInstName, myModules[x]);
                QColor color;
                if (!mi.place(anchor)) {
                    color = QColor.red;
                    System.out.println("ERROR: Failed to place module " + tileInstName);
                } else {
                    color = QColor.green;
                }

                try {
                    final RelocatableTileRectangle bb = myModules[x].getBoundingBox().getCorresponding(anchor.getTile(), origAnchor.getTile());
                    rects.add(new UiPBlock(bb, tileInstName, color));
                } catch (NullPointerException e) {
                    //So far outside the rect does not even fit anymore...
                    e.printStackTrace();
                }

                anchor = anchor.getNeighborSite(0,-yDiff);

            }



        }
/*
        for (Module m : myModules) {
            Site origAnchor = m.getAnchor().getSite();
            m.calculateAllValidPlacements(m.getDevice());
            for (Site anchor : m.getAllValidPlacements()) {

                try {
                    final RelocatableTileRectangle bb = m.getBoundingBox().getCorresponding(anchor.getTile(), origAnchor.getTile());
                    rects.add(new UiPBlock(bb, anchor.getName(), QColor.green));
                } catch (NullPointerException e) {
                    //So far outside the rect does not even fit anymore...
                    e.printStackTrace();
                }
            }
        }*/



        // This line fixes slow performance under Linux
        QApplication.setGraphicsSystem("raster");
        QApplication.initialize(new String[]{});
        final PblockScene pblockScene = new PblockScene(design);
        pblockScene.setBlocks(rects);
        TileWindow.showBlocking(pblockScene);

    }
}
