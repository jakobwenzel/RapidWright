package com.xilinx.rapidwright.summer20;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QColor;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.RelocatableTileRectangle;
import com.xilinx.rapidwright.design.TileRectangle;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.design.blocks.PBlockRange;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.device.helper.TileColumnPattern;
import com.xilinx.rapidwright.examples.TileWindow;
import com.xilinx.rapidwright.util.NoCloseOutputStream;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.util.Utils;

public class CreateArrayConstraints {

    final static int left_dummy_gap = 2;
    final static int right_dummy_gap = 2;
    final static int top_dummy_gap = 2;
    final static int bottom_dummy_gap = 2;

    final static int left_dummy_width = 3;
    final static int right_dummy_width = 3;
    final static int top_dummy_height = 4;
    final static int bottom_dummy_height = 4;


    final static int tile_brams = 6;
    final static int tile_urams = 3;
    public final static String deviceName = "xcu250-figd2104-2L-e";

    protected static Device device;

    private boolean rectTypeToPblock(PrintWriter pw, String name, String type, TileRectangle rect, int neededCount) {
        final List<Site> sites = rect.streamTiles(device)
                .flatMap(tile -> Arrays.stream(tile.getSites()))
                .filter(site -> site.getNameSpacePrefix().equals(type))
                .collect(Collectors.toList());

        boolean countOk = true;
        if (neededCount > 0) {
            final long available = sites.size();
            if (neededCount > available) {
                System.err.println("not enough "+type+" available for pblock. we need " + neededCount + " but only " + available + " exist at location");
                countOk = false;
            } else {
                System.out.println(available + " "+type+" available: " + sites);
            }
        }

        final PBlockRange pblock = PBlock.createPBlockRange(device, sites);

        if (pblock != null) {
            pw.println("resize_pblock [get_pblocks " + name + "] -add {" + pblock + "}");
        }
        return countOk;
    }

    private Pair<RelocatableTileRectangle, Boolean> make_pblock(PrintWriter pw, String name, String cell, int left, int right, int top, int bottom, boolean cellsHierarchical, boolean setParent, int neededBrams, int neededUrams) {
        pw.println("create_pblock " + name);
        pw.println("add_cells_to_pblock [get_pblocks " + name + "] [get_cells " + (cellsHierarchical ? "-hierarchical " : "") + "-quiet [list " + cell + "]]");
        String sliceBL = "SLICE_X" + left + "Y" + bottom;
        final String sliceTR = "SLICE_X" + right + "Y" + top;
        pw.println("resize_pblock [get_pblocks " + name + "] -add {" + sliceBL + ":" + sliceTR + "}");
        if (setParent) {
            pw.println("set_property PARENT tile [get_pblocks " + name + "]");
        }
        if (!cellsHierarchical) {
            pw.println("");
        }
        if (neededBrams > 0 || neededUrams > 0) {
            final Site siteBL = device.getSite(sliceBL);
            final Site siteTR = device.getSite(sliceTR);
            final RelocatableTileRectangle rect = RelocatableTileRectangle.of(siteBL.getTile(), siteTR.getTile());

            /*rect.streamTiles(device)
                    .flatMap(tile -> Arrays.stream(tile.getSites()))
                    .map(site->site.getNameSpacePrefix()+": "+site.getSiteTypeEnum())
                    .distinct()
                    .forEach(System.out::println);*/


            boolean countURAMOk = rectTypeToPblock(pw, name,"URAM288_", rect, neededUrams);
            boolean countBRAMOk = rectTypeToPblock(pw, name,"RAMB36_", rect, neededBrams);


            return new Pair<>(rect, countBRAMOk && countURAMOk);
        }
        return null;
    }

    protected Pair<RelocatableTileRectangle, Boolean> print_pattern_xdc(int idx, int tile_left_slice, int tile_bottom_slice, int tile_slice_width, int tile_slice_height, boolean isBramTile) {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get("ramtype"+idx+".txt")))) {
            if (isBramTile) {
                pw.println("bram");
            } else {
                pw.println("uram");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String filename = "pattern" + idx + ".xdc";

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get(filename)))) {


            int tile_right_slice = tile_left_slice + tile_slice_width - 1;
            int tile_top_slice = tile_bottom_slice + tile_slice_height - 1;

            pw.println("create_clock -period 3.200 -name TS_clk_line -waveform {0.000 1.6} [get_ports clk_line]");
            pw.println("create_clock -period 8.000 -name TS_clk_control -waveform {0.000 4.000} [get_ports clk_control]");
            pw.println("set_false_path -from [get_ports clk_line_rst_high]");
            pw.println("set_false_path -from [get_ports clk_control_rst_low]");

            pw.println("set_false_path -from [get_ports clk_line_rst_high]");
            pw.println("set_false_path -from [get_ports clk_line_rst_high]");
            pw.println("set_false_path -from [get_ports clk_line_rst_high]");


            pw.println("set_clock_groups -group TS_clk_line -group TS_clk_control -asynchronous");

            pw.println("# change bufg locations to the one used in monolithic top-down implementation");
            pw.println("#set_property HD.CLK_SRC BUFGCTRL_X1Y73 [get_ports clk_line]");
            pw.println("#set_property HD.CLK_SRC BUFGCTRL_X1Y69 [get_ports clk_control]");


            pw.println("# User Generated physical constraints ");
            pw.println("");
            pw.println("startgroup ");

            Pair<RelocatableTileRectangle, Boolean> res = make_pblock(pw, "tile", "tile", tile_left_slice, tile_right_slice, tile_top_slice, tile_bottom_slice, true, false, isBramTile?tile_brams:0,  isBramTile?0:tile_urams);

            //pw.println("resize_pblock [get_pblocks tile] -add {RAMB18_X7Y216:RAMB18_X7Y227}");
            //pw.println("resize_pblock [get_pblocks tile] -add {RAMB36_X7Y108:RAMB36_X7Y113}");
            pw.println("endgroup ");
            pw.println("set_property CONTAIN_ROUTING 1 [get_pblocks tile]");
            pw.println("set_property EXCLUDE_PLACEMENT 1 [get_pblocks tile]");
            pw.println("set_property IS_SOFT FALSE [get_pblocks tile]");
            pw.println("#set_property HD.PARTPIN_RANGE SLICE_X" + tile_left_slice + "Y" + tile_bottom_slice + ":SLICE_X" + tile_right_slice + "Y" + tile_top_slice + " [get_pins tile/*stream*TDATA*]");

            pw.println("# switches at peripheral");
            make_pblock(pw, "sw_top", "tile/switch2_to_top", tile_left_slice, tile_right_slice, tile_top_slice, tile_top_slice - 2, false, true, 0, 0);
            make_pblock(pw, "sw_left", "tile/switch1_to_left", tile_left_slice, tile_left_slice + 2, tile_top_slice - 1, tile_bottom_slice + 1, false, true, 0, 0);
            make_pblock(pw, "sw_bottom", "tile/switch4_to_bottom", tile_left_slice, tile_right_slice, tile_bottom_slice + 2, tile_bottom_slice, false, true, 0, 0);
            make_pblock(pw, "sw_right", "tile/switch3_to_right", tile_right_slice - 2, tile_right_slice, tile_top_slice - 1, tile_bottom_slice + 1, false, true, 0, 0);

            pw.println("#removable switches to emulate the context");
            make_pblock(
                    pw,
                    "pblock_top_dummy", "top_dummy", tile_left_slice,
                    tile_right_slice,
                    tile_top_slice + top_dummy_gap + top_dummy_height,
                    tile_top_slice + top_dummy_gap,
                    false,
                    false,
                    0, 0);

            make_pblock(
                    pw,
                    "pblock_left_dummy", "left_dummy", tile_left_slice - left_dummy_gap - left_dummy_width,
                    tile_left_slice - left_dummy_gap,
                    tile_top_slice - 1,
                    tile_bottom_slice + 1,
                    false,
                    false,
                    0, 0);

            make_pblock(
                    pw,
                    "pblock_right_dummy", "right_dummy", tile_right_slice + right_dummy_gap,
                    tile_right_slice + right_dummy_gap + right_dummy_width,
                    tile_top_slice - 1,
                    tile_bottom_slice + 1,
                    false,
                    false,
                    0, 0);

            make_pblock(
                    pw,
                    "pblock_bottom_dummy", "bottom_dummy", tile_left_slice,
                    tile_right_slice,
                    tile_bottom_slice - bottom_dummy_gap,
                    tile_bottom_slice - bottom_dummy_gap - bottom_dummy_height,
                    false,
                    false,
                    0, 0);

            //pw.println("#create_pblock pblock_S_CONTROL_USS");
            //pw.println("#add_cells_to_pblock [get_pblocks pblock_S_CONTROL_USS] [get_cells -quiet [list S_CONTROL_USS]]");
            //pw.println("#resize_pblock [get_pblocks pblock_S_CONTROL_USS] -add {SLICE_X112Y500:SLICE_X125Y530}");
            //pw.println("#set_property CONTAIN_ROUTING 1 [get_pblocks pblock_S_CONTROL_USS]");
            //pw.println("");

            return res;

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void makeShellPblock(PrintWriter pw, String name, TileRectangle rect) {

        final List<String> constrainedTypes = Arrays.asList(
                "SLICE_",
                "DSP48E2_",
                "RAMB18_",
                "RAMB36_",
                "URAM288_"
        );


        for (String type : constrainedTypes) {
            rectTypeToPblock(pw, name, type, rect, 0);
        }

    }

    private void writeShellPblock(RelocatableTileRectangle arrayRect, RelocatableTileRectangle outer) {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new NoCloseOutputStream(System.out)));


        makeShellPblock(pw, "pblock_1_exclude_tiles", arrayRect);
        makeShellPblock(pw, "pblock_2_contain_user_shell", outer);
        pw.flush();
    }

    public static void main(String[] args) {
        new CreateArrayConstraints().run(args);

    }

    protected void createBlocks(String[] args, List<UiPBlock> rects) {
        int n = 4; //Integer.parseInt(args[0]);
        int left = 95; //Integer.parseInt(args[1]);    //5, #60
        //bottom = 660 + 2;    #542; #shifted up two
        int bottom = 540; //Integer.parseInt(args[2]);  //630 + 2; #542; #shifted up two
        int width = 17; //Integer.parseInt(args[3]);   //10; #25
        int height = 30; //Integer.parseInt(args[4]);  //60 - 4 - 2; #27; #reduced by two

        System.out.println("n = " + n);
        System.out.println("left = " + left);
        System.out.println("bottom = " + bottom);
        System.out.println("width = " + width);
        System.out.println("height = " + height);


        for (int i = 0; i < n; i++) {
            //int tile_left = left + (width * i);

            int tile_left;
            switch (i) {
                case 0: tile_left = 31; width = 56-tile_left+1; break;
                case 1: tile_left = 57; width = 76-tile_left+1; break;
                case 2: tile_left = 77; width = 94-tile_left+1; break;
                case 3: tile_left = 95; width = 116-tile_left+1; break;
                default: {
                    throw new RuntimeException("invalid index "+i);
                }
            }

            Pair<RelocatableTileRectangle, Boolean> rect = print_pattern_xdc(i + 1, tile_left, bottom, width, height, true);
            addPblockInstances(rects, i, rect.getFirst(), rect.getSecond(), true, width);
        }
    }

    protected void addPblockInstances(List<UiPBlock> rects, int x, RelocatableTileRectangle rect, boolean bramCountOk, boolean isBramTile, int sliceCols) {
        final Site anchor = rect.streamTiles(device).flatMap(tile -> Arrays.stream(tile.getSites())).filter(site -> site.getNameSpacePrefix().equals("SLICE_"))
                .findAny().orElseThrow(() -> new RuntimeException("No slice found in constraints"));

        final Map<Integer, RelocatableTileRectangle> ramRects = rect.streamTiles(device)
                .filter(tile -> Utils.isBRAM(tile.getTileTypeEnum()) || Utils.isURAM(tile.getTileTypeEnum()))
                .collect(Collectors.groupingBy(Tile::getTileXCoordinate, RelocatableTileRectangle.collector()));

        if (ramRects.isEmpty()) {
            System.err.println("no rams found in rect!");
        }

        boolean knownProblematic = false; //Arrays.asList(8,9,11,14).contains(x+1);
        QColor color = (!bramCountOk||knownProblematic) ?  QColor.darkRed :  QColor.green;
        /*if (knownProblematic) {
            color = color.darker().darker();
        }*/
        for (int y = 3; y>=0; y--) {
            int offset = 90-y*30;

            Site newAnchor = anchor.getNeighborSite(0, offset);

            final String ramXes = ramRects.values().stream().mapToInt(RelocatableTileRectangle::getMinColumn).sorted().mapToObj(Integer::toString).collect(Collectors.joining(", "));
            rects.add(new UiPBlock(rect.getCorresponding(newAnchor.getTile(), anchor.getTile()), "tile"+(y +1)+"_"+(x+1)+"\n"+ sliceCols, color));
            for (RelocatableTileRectangle ramRect : ramRects.values()) {
                try {
                    QColor ramColor = Utils.isBRAM(ramRect.getMaxRowTile().getTileTypeEnum()) ? QColor.blue : QColor.white;
                    rects.add(new UiPBlock(ramRect.getCorresponding(newAnchor.getTile(), anchor.getTile()), null, ramColor));
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void run(String[] args) {
        device = Device.getDevice(deviceName);
        List<UiPBlock> rects = new ArrayList<>();
        createBlocks(args, rects);

        final RelocatableTileRectangle arrayRect = rects.stream().flatMap(r -> r.rect.streamTiles(device)).collect(RelocatableTileRectangle.collector());
        rects.add(0, new UiPBlock(arrayRect, null, QColor.yellow));

        RelocatableTileRectangle outer = makeOuter(arrayRect);
        rects.add(0, new UiPBlock(outer, null, QColor.blue));

        writeShellPblock(arrayRect, outer);


        final RelocatableTileRectangle exampleRow = IntStream.range(0, device.getColumns()).mapToObj(col -> device.getTile(TileColumnPattern.getCommonRow(device), col)).collect(RelocatableTileRectangle.collector());
        rects.add(new UiPBlock(exampleRow, "example", QColor.blue));

        // This line fixes slow performance under Linux
        QApplication.setGraphicsSystem("raster");
        QApplication.initialize(new String[]{});
        Design dummyDesign = new Design("dummy", device.getName());
        final PblockScene pblockScene = new PblockScene(dummyDesign);
        pblockScene.setBlocks(rects);
        TileWindow.showBlocking(pblockScene);
    }


    private static int towardsZero(int i) {
        if (i > 0) {
            return i-1;
        } else if (i < 0) {
            return i+1;
        }
        return i;

    }
    private static Tile tryFindWithOffset(Tile original, int xDiff, int yDiff) {

        Tile res;
        while ((res=original.getTileXYNeighbor(xDiff, yDiff))==null) {
            xDiff = towardsZero(xDiff);
            yDiff = towardsZero(yDiff);
        }
        /*if (res == original) {
            return null;
        }*/
        return res;
    }

    private static RelocatableTileRectangle makeOuter(RelocatableTileRectangle arrayRect) {
        int xDiff = 10;
        int yDiff = 20;
        return new RelocatableTileRectangle(
                tryFindWithOffset(arrayRect.getMinColumnTile(), -xDiff,0),
                tryFindWithOffset(arrayRect.getMaxColumnTile(), xDiff,0),
                tryFindWithOffset(arrayRect.getMinRowTile(), 0,yDiff),
                tryFindWithOffset(arrayRect.getMaxRowTile(), 0,-yDiff)
        );
    }
}
