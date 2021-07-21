package com.xilinx.rapidwright.summer20;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.RelocatableTileRectangle;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.device.helper.TileColumnPattern;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.util.Utils;

public class CreateArrayConstraintsFullWidth extends CreateArrayConstraints{
    public static void main(String[] args) {
        new CreateArrayConstraintsFullWidth().run(args);
    }

    @Override
    protected void createBlocks(String[] args, List<UiPBlock> rects) {

        final HashMap<TileColumnPattern, TreeSet<Integer>> patterns = TileColumnPattern.genColumnPatternMap(device);
        int checkedRow = TileColumnPattern.getCommonRow(device);
        final List<TileColumnPattern> ttes = patterns.keySet().stream()
                .filter(tileTypeEnums -> tileTypeEnums.size() == 1 && (tileTypeEnums.hasBRAM() || tileTypeEnums.hasURAM()))
                .collect(Collectors.toList());


        final Set<Integer> brams = ttes.stream().filter(TileColumnPattern::hasBRAM).flatMap(tte -> patterns.get(tte).stream()).collect(Collectors.toSet());

        final Set<Integer> rams = ttes.stream().flatMap(tte -> patterns.get(tte).stream()).sorted().collect(Collectors.toCollection(TreeSet::new));

        /*final TreeSet<Integer> brams = patterns.get(Arrays.asList(TileTypeEnum.BRAM));
        final TreeSet<Integer> urams = patterns.get(Arrays.asList(TileTypeEnum.URAM_URAM_DELAY_FT));
        Set<Integer> rams = Stream.concat(brams.stream(), urams.stream()).collect(Collectors.toSet());*/


        final Stream<Integer> clbColumns = patterns.keySet().stream().filter(t -> t.size() == 1 && Utils.isCLB(t.get(0)))
                .flatMap(t -> patterns.get(t).stream())
                .sorted();
        //final Map<Integer, List<Integer>> byRam = divideByClosestBram(clbColumns, rams);
        final Map<Integer, List<Integer>> byRam = divideManually(clbColumns, rams);
        //divideStartingFromBram(clbColumns, rams, byRam);

        int minWidth = Integer.MAX_VALUE;
        for (Integer ram : rams) {
            boolean isBram = brams.contains(ram);
            System.out.println((isBram?'B':'U')+"ram at "+ram);
            final List<Integer> atBram = byRam.get(ram);
            if (atBram == null) {
                continue;
            }
            System.out.println("\t" + atBram);
            System.out.println("\t" + atBram.size() + " slice cols");
            if (atBram.size() < minWidth) {
                minWidth = atBram.size();
            }
        }
        System.out.println("smallest tile has "+minWidth+" slice cols");


        int i = 0;
        for (Integer bram : rams) {
            if (byRam.get(bram) != null) {
                toPblock(bram, byRam, checkedRow, i++, rects, brams);
            }
        }

    }

    private void divideStartingFromBram(Stream<Integer> clbColumnsStream, Set<Integer> rams, Map<Integer, List<Integer>> byRam) {
        final List<Integer> clbColumns = clbColumnsStream.collect(Collectors.toList());
        while (!clbColumns.isEmpty()) {
            for (Integer ram : rams) {
                if (clbColumns.isEmpty()) {
                    break;
                }
                Integer column = selectNextColumn(clbColumns, ram, rams);
                if (column != null) {
                    clbColumns.remove(column);
                    byRam.get(ram).add(column);
                }
            }
        }
    }

    private Integer selectNextColumn(List<Integer> clbColumns, int bram, Set<Integer> brams) {
        int minDistance = Integer.MAX_VALUE;
        int selected = -1;
        for (int col : clbColumns) {
            int dist = Math.abs(col-bram);
            if (dist < minDistance) {
                selected = col;
                minDistance = dist;
            }
        }

        //Is there another BRAM between selected and bram?
        int b = Math.max(selected, bram);
        int s = Math.min(selected, bram);
        for (int otherBram : brams) {
            if (b > otherBram && otherBram > s) {
                return null;
            }
        }

        return selected;
    }

    private Map<Integer, List<Integer>> divideByClosestBram(Stream<Integer> clbColumns, Set<Integer> rams) {

        final Map<Integer, List<Integer>> byRam = rams.stream()
                .collect(Collectors.toMap(Function.identity(), x-> new ArrayList<>()));
        clbColumns
                .forEach(sliceCol -> {
                    int bram = findClosestRam(sliceCol, rams);
                    byRam.get(bram).add(sliceCol);
                });
        return byRam;
    }


    private int getStartingClb(int ram) {
        switch (ram) {
            case 75:
                return 53;
            case 97:
                return 93;
            case 143:
                return 122;
            case 165:
                return 153;
            case 202:
                return 183;
            case 215:
                return 212;
            case 288:
                return 252;
            case 297:
                return 294;
            case 328:
                return 316;
            case 353:
                return 341;
            case 425:
                return 419;
            case 447:
                return 444;
            case 489:
                return 468;
            case 502:
                return 495;
            case 524:
                return 521;
            case 582:
                return 555;
            case 604:
                return 596;
            case 686:
                return 647;
            case 709:
                return 703;
            default: throw new RuntimeException("invalid ram: "+ram);
        }
    }

    private int getStartingClbV2(int ram) {
        switch (ram) {
            case 75:
                return 53;
            case 97:
                return 93;
            case 143:
                return 122;
            case 165:
                return 153;
            case 202:
                return 183;
            case 215:
                return 212;
            case 288:
                return 252;
            case 297:
                return 294;
            case 328:
                return -1;
                //return 316;
            case 353:
                return 334;
            case 425:
                return 363;
            case 447:
                return 444;
            case 489:
                return 468;
            case 502:
                //return 495;
                return -1;
            case 524:
                return 521;
            case 582:
                return 555;
            case 604:
                return 596;
            case 686:
                return 647;
            case 709:
                return 703;
            default: throw new RuntimeException("invalid ram: "+ram);
        }
    }

    private Map<Integer, List<Integer>> divideManually(Stream<Integer> clbColumns, Set<Integer> rams) {

        final Function<Integer, Integer> startingClbFunc = this::getStartingClbV2;
        final Map<Integer, List<Integer>> byRam = rams.stream()
                .filter(ram->startingClbFunc.apply(ram) >=0)
                .collect(Collectors.toMap(Function.identity(), x-> new ArrayList<>()));
        clbColumns.forEach(clb -> {
            final Integer ram = rams.stream()
                    .sorted(Comparator.reverseOrder())
                    .filter(r -> {
                        final int startCol = startingClbFunc.apply(r);
                        return startCol >= 0 && startCol <= clb;
                    })
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("no ram found for " + clb));
            byRam.get(ram).add(clb);
        });
        return byRam;

    }

    private void toPblock(int ram, Map<Integer, List<Integer>> byRam, int checkedRow, int i, List<UiPBlock> rects, Set<Integer> brams) {
        final List<Integer> sliceCols = byRam.get(ram);
        final IntSummaryStatistics stats = sliceCols.stream().mapToInt(c -> c).summaryStatistics();
        final int leftSlice = getSliceX(stats.getMin(), checkedRow);
        final int rightSlice = getSliceX(stats.getMax(), checkedRow);

        final int bottom = 540;
        final int height = 30;

        boolean isBramTile = brams.contains(ram);

        Pair<RelocatableTileRectangle, Boolean> rect = print_pattern_xdc(i + 1, leftSlice, bottom, rightSlice-leftSlice+1, height, isBramTile);
        addPblockInstances(rects, i, rect.getFirst(), rect.getSecond(), isBramTile, sliceCols.size());
    }

    private int getSliceX(int tileX, int tileY) {
        final Tile tile = device.getTile(tileY, tileX);
        if (!Utils.isCLB(tile.getTileTypeEnum())) {
            throw new RuntimeException("Unexpected tile: "+tile);
        }
        if (tile.getSites().length != 1) {
            throw new RuntimeException("not one site");
        }
        final Site site = tile.getSites()[0];
        if (!Utils.isSLICE(site.getSiteTypeEnum())) {
            throw new RuntimeException("Unexpected site: "+site);
        }
        final int instanceX = site.getInstanceX();
        if (instanceX < 0) {
            throw new RuntimeException("Invalid x for "+site);
        }
        return instanceX;
    }

    private int findClosestRam(int sliceCol, Set<Integer> rams) {
        int bestBram = -1;
        double bestDiff = Double.POSITIVE_INFINITY;



        //Problem for 121, 459
        OUTER: for (int bram : rams) {

            double diff = Math.abs(sliceCol - bram);
            //TODO fix having to do these overrides
            /*if ((sliceCol == 121 || sliceCol == 459) && (bram<sliceCol)) {
                diff/=2;
            }*/


            if (diff < bestDiff) {
                bestDiff = diff;
                bestBram = bram;
            }
        }
        return bestBram;
    }
}
