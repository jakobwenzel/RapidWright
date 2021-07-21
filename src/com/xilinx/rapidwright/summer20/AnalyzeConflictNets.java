package com.xilinx.rapidwright.summer20;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.trolltech.qt.gui.QApplication;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.device.Node;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.examples.TileWindow;
import com.xilinx.rapidwright.util.Pair;

public class AnalyzeConflictNets {
    private static Stream<Node> getNetNodes(Net net) {
        final Map<Node, ArrayList<PIP>> nodePIPMap = DesignTools.getNodePIPMap(net.getPIPs());
        return nodePIPMap.keySet().stream();
    }
    public static void main(String[] args) {
        Design design = Design.readCheckpoint(args[0]);

        analyzeOverlapping(design);
    }

    public static void analyzeOverlapping(Design design) {
        final Map<Node, List<Net>> overlaps = design.getNets().stream()
                .flatMap(n -> getNetNodes(n).map(node -> new Pair<>(n, node)))
                .collect(Collectors.groupingBy(Pair::getSecond, Collectors.mapping(Pair::getFirst, Collectors.toList())))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        overlaps.forEach((node, nets) -> {
            System.out.println("Overlap at "+node+": "+getTileInfo(node.getTile(), design));

            for (Net net : nets) {
                System.out.println("\t"+net);
            }
        });

        final long totalNets = overlaps.values().stream().flatMap(Collection::stream).distinct().count();
        System.out.println(totalNets+" overlapped nets");
        System.out.println(overlaps.size()+" overlapped nodes");

        if (overlaps.size() > 0 ) {
            final Map<Tile, Long> overlapsByTile = overlaps.keySet().stream().collect(Collectors.groupingBy(Node::getTile, Collectors.counting()));

            // This line fixes slow performance under Linux
            QApplication.setGraphicsSystem("raster");
            QApplication.initialize(new String[]{});
            final TileDataScene<Long> tileDataScene = new TileDataScene<>(design, overlapsByTile);

            //UiTools.saveAsPdf(tileDataScene, FileTools.replaceExtension(Paths.get(args[0]), ".pdf").toFile());
            TileWindow.showBlocking(tileDataScene);
        }

        final Map<Tile, Set<ModuleInst>> tileToModules = design.getSiteInsts().stream()
                .collect(Collectors.groupingBy(
                        si -> si.getSite().getTile(),
                        Collectors.mapping(SiteInst::getModuleInst, Collectors.toSet())
                ));

        tileToModules.forEach(((tile, moduleInsts) -> {
            if (moduleInsts.size()>1) {
                System.out.println("overlap at "+tile);
                System.out.println(moduleInsts);
            }
        }));

        //HandPlacer.openDesign(design);
        /*final Map<Integer, Set<String>> byColumn = design.getSiteInsts().stream()
                .filter(si->si.getModuleInst()!=null)
                .collect(Collectors.groupingBy(si -> si.getTile().getColumn(),
                Collectors.mapping(si -> si.getModuleInst().getName(), Collectors.toSet())));

        final Map<Integer, Set<String>> nameRoots = design.getSiteInsts().stream()
                .filter(si -> si.getModuleInst() != null)
                .collect(Collectors.groupingBy(si -> si.getTile().getColumn(),
                        Collectors.mapping(si -> tileNameWithoutY(si.getTile()), Collectors.toSet())));
        final Map<Integer, Long> colCount = design.getSiteInsts().stream()
                .filter(si -> si.getModuleInst() != null)
                .collect(Collectors.groupingBy(si -> si.getTile().getColumn(), Collectors.counting()));

        byColumn.keySet().stream().sorted()
            .forEach(col-> {
                final String nameRoot = String.join(", ", nameRoots.get(col));
                System.out.println(col+": "+byColumn.get(col)+", "+nameRoot+", "+colCount.get(col));
            });*/
    }

    private static String tileNameWithoutY(Tile tile) {
        int index = tile.getName().lastIndexOf("Y");
        return tile.getName().substring(0, index+1)+"*";
    }

    private static String getTileInfo(Tile tile, Design design) {
        return design.getModuleInsts()
                .stream().filter(mi->mi.getBoundingBox().isInside(tile))
                .map(mi->"Inside "+mi.getName())
                .collect(Collectors.joining(", "));
    }


}
