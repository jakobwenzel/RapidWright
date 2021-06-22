package com.xilinx.rapidwright.summer20;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;

public class AnalyzeConflicts {
    public static void main(String[] args) throws IOException {
        Design design = Design.readCheckpoint("final.dcp");
        final List<String> conflictnets = Files.readAllLines(Paths.get("conflictnets.txt"));

        Set<String> allRootInsts = new HashSet<>();
        for (String netName : conflictnets) {
            if (netName.isEmpty()) {
                continue;
            }
            final Net net = design.getNet(netName);
            if (net == null) {
                throw new RuntimeException("Did not find net "+netName);
            }

            if (net.getLogicalNet() == null) {
                throw new RuntimeException("no logical net for "+net);
            }
            final Set<String> rootInsts = net.getPins().stream()
                    .flatMap(pin -> pin.getSiteInst().getCells().stream())
                    .map(cell -> getRootInstName(cell.getName()))
                    .collect(Collectors.toSet());

            if (rootInsts.size()!=1) {
                System.out.println("for "+net+" we have "+rootInsts.size()+" root insts:");
                rootInsts.stream().sorted().forEach(i-> System.out.println("\t"+i));
            }
            allRootInsts.addAll(rootInsts);
        }

        System.out.println("List of all root Insts");
        allRootInsts.stream().sorted().forEach(i-> System.out.println("\t"+i));

    }

    private static String getRootInstName(String name) {
        int slashLoc = name.indexOf('/');
        if (slashLoc == -1) {
            return name;
        }
        return name.substring(0,slashLoc);
    }
}
