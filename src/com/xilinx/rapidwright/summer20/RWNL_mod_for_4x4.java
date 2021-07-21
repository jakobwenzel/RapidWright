package com.xilinx.rapidwright.summer20;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.RelocatableTileRectangle;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.TileRectangle;
import com.xilinx.rapidwright.util.Pair;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;

public class RWNL_mod_for_4x4 {

    public static void main(String[] args) {
        OptionParser optionParser = new OptionParser();
        final OptionSpecBuilder optOption = optionParser.accepts("opt");
        final NonOptionArgumentSpec<String> pathOption = optionParser.nonOptions("Tile Path");

        final OptionSet options = optionParser.parse(args);

        boolean isOpt = options.has(optOption);


        Design design;
        if (isOpt)
            design = Design.readCheckpoint("./USS_opt_u250.dcp");
        else
            design = Design.readCheckpoint("./shell.dcp");

        Path patternDir = Paths.get(options.valueOf(pathOption));
        if (!Files.isDirectory(patternDir)) {
            throw new RuntimeException("not a dir at "+patternDir);
        }

        Module[] myModules = loadModules(patternDir, 4);

        for(int i = 0; i < myModules.length; i++) {
            Module myModule = myModules[i];
            design.addModule(myModule);
            design.getNetlist().migrateCellAndSubCells(myModule.getNetlist().getTopCell(), true);
        }

        // Create and place instances of tile modules on user shell
        long startTime = System.nanoTime();        

        for(int i = 0; i < myModules.length; i++) {
            String xName = Integer.toString(i + 1);

            String[] tileInsts = new String[]{"tile1_" + xName, "tile2_" + xName, "tile3_" + xName, "tile4_" + xName};
            int position = 90;//30; // 0
            for (String tileInstName : tileInsts) {
                System.out.println("tileInst:"+tileInstName);
                DesignTools.makeBlackBox(design, tileInstName);
                final Module module = myModules[i];
                ModuleInst mi = design.createModuleInst(tileInstName, module);
                if (!mi.place(module.getAnchor().getSite().getNeighborSite(0, position))) {
                    System.err.println("ERROR: Failed to place module " + tileInstName);
                }
                position = position - 30; // - 60
            }

        }
        //unrouteCrossArrayNets(design);
        //HandPlacer.openDesign(design);


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

    public static Module[] loadModules(Path patternDir) {
        return loadModules(patternDir, -1);
    }
    public static Module[] loadModules(Path patternDir, int count) {
        try (final Stream<Path> files = Files.list(patternDir)) {
            final Module[] modules = files
                    .filter(f -> {
                        final String fn = f.getFileName().toString();
                        return fn.startsWith("tile_p") && fn.endsWith(".dcp");
                    })
                    .sorted(Comparator.comparing(Object::toString))
                    .map(f->new Pair<>(f, f.getFileName().toString().replaceAll("tile_p", "").replace(".dcp", "")))
                    .filter(p->{
                        String name = p.getSecond();
                        int id = Integer.parseInt(name);
                        return count < 0 || id <= count;
                    })
                    .map(p -> {
                        final Module module = new Module(Design.readCheckpoint(p.getFirst()));
                        module.setName(p.getSecond());
                        return module;
                    })
                    .toArray(Module[]::new);
            if (modules.length == 0) {
                throw new RuntimeException("No modules found at "+patternDir);
            }
            System.out.println("Loaded "+modules.length+" modules");
            return modules;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
