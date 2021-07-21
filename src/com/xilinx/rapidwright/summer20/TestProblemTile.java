package com.xilinx.rapidwright.summer20;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.util.Pair;

public class TestProblemTile {
    private static String stripInst(String s) {
        return s.replaceAll("inst/","");
    }
    private static void analyze(Design d, String name) {
        final SiteInst problemSi = d.getSiteInstFromSite(d.getDevice().getSite("SLICE_X178Y550"));
        final Path dir = Paths.get("dump");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(dir.resolve(name)))) {

            pw.println("CTAGS");
            pw.println("=====");
            problemSi.getSiteCTags().entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(Net::getName)))
                    .forEach(entry -> pw.println(stripInst("\t"+entry.getKey()+"="+getSorted(entry.getValue()))));
            pw.println("SitePinInstMap");
            pw.println("==============");
            problemSi.getSitePinInstMap().entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> pw.println(stripInst("\t"+entry)));

            pw.println("NetSiteWireMap");
            pw.println("==============");
            problemSi.getNetSiteWireMap().entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> pw.println(stripInst("\t"+entry)));

            problemSi.getCells().stream().sorted(Comparator.comparing(Cell::getName))
                    .forEach(cell -> {
                        final String title = stripInst("CELL " + cell.getName()+ " at "+cell.getBEL());
                        pw.println(title);
                        pw.println(title.replaceAll(".","="));

                        cell.getPinMappingsP2L().entrySet()
                                .stream()
                                .sorted(Map.Entry.comparingByKey())
                                .forEach(entry -> pw.println(stripInst("\t"+entry)));
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String getSorted(HashSet<?> value) {
        return value.stream().map(Object::toString).sorted().collect(Collectors.joining(",", "[", "]"));
    }


    private static Module loadModule(String dir) {
        Design loaded = Design.readCheckpoint(Paths.get(dir).resolve("tile_p12.dcp"));
        analyze(loaded, "original");
        loaded.writeCheckpoint("directly_written.dcp");
        return new Module(loaded);
    }

    public static void main(String[] args) {
        {

            Module module = loadModule(args[0]);


            Design design = new Design("design", "xcu250");


            design.addModule(module);
            design.getNetlist().migrateCellAndSubCells(module.getNetlist().getTopCell(), true);

            final ModuleInst inst = design.createModuleInst("inst", module);
            final boolean place = inst.place(module.getAnchor().getSite());
            if (!place) {
                throw new RuntimeException("failed to place");
            }

            analyze(design, "as module");

            design.writeCheckpoint("asModule.dcp");

        }

        {
            Design reread = Design.readCheckpoint("asModule.dcp");
            analyze(reread, "reread ");
            //Design reread = Design.readCheckpoint((Paths.get(args[0]).resolve("tile_p12.dcp")));
            //AnalyzeConflictNets.analyzeOverlapping(reread);


            reread.getNets().stream().flatMap(n -> n.getPins().stream().map(p -> new Pair<>(p, n)))
                    .collect(Collectors.groupingBy(Pair::getFirst, Collectors.mapping(Pair::getSecond, Collectors.toList())))
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().size() != 1)
                    .forEach(e -> {
                        System.out.println("SPI overuse at " + e.getKey());
                        System.out.println("\t" + e.getValue());
                    });

            try (PrintStream ps = new PrintStream(Files.newOutputStream(Paths.get("allplacements_rw.txt")))) {
                reread.getCells()
                        .stream()
                        .map(c -> c.getSiteInst().getSite().getName() + "." + c.getSiteInst().getSiteTypeEnum() + "." + c.getBELName() + "=inst/" + c.getName())
                        .sorted()
                        .forEach(ps::println);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        {
            Design reread = Design.readCheckpoint("asModuleVivadoSave.dcp");
            analyze(reread, "vivado");
            //Design reread = Design.readCheckpoint((Paths.get(args[0]).resolve("tile_p12.dcp")));
            //AnalyzeConflictNets.analyzeOverlapping(reread);


            reread.getNets().stream().flatMap(n -> n.getPins().stream().map(p -> new Pair<>(p, n)))
                    .collect(Collectors.groupingBy(Pair::getFirst, Collectors.mapping(Pair::getSecond, Collectors.toList())))
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue().size() != 1)
                    .forEach(e -> {
                        System.out.println("SPI overuse at " + e.getKey());
                        System.out.println("\t" + e.getValue());
                    });

            try (PrintStream ps = new PrintStream(Files.newOutputStream(Paths.get("allplacements_rw.txt")))) {
                reread.getCells()
                        .stream()
                        .map(c -> c.getSiteInst().getSite().getName() + "." + c.getSiteInst().getSiteTypeEnum() + "." + c.getBELName() + "=inst/" + c.getName())
                        .sorted()
                        .forEach(ps::println);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
