package com.xilinx.rapidwright.summer20;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.device.Wire;

public class CompareTiles {
    public static void main(String[] args) {
        Device dev = Device.getDevice("xcu250");
        final Tile a = dev.getTile(args[0]);
        final Tile b = dev.getTile(args[1]);
        compareTiles(a,b);
    }

    private static void compareTiles(Tile a, Tile b) {
        if (a.getWireCount() != b.getWireCount()) {
            System.out.println("differing wire count");
            return;
        }
        if (!Arrays.equals(a.getWireNames(), b.getWireNames())) {
            System.out.println("differing wire names");
        }
        for (int i = 0; i < a.getWireCount(); i++) {
            final List<Wire> aConn = a.getWireConnections(i);
            final List<Wire> bConn = b.getWireConnections(i);
            if (!a.getWireName(i).equals(b.getWireName(i))) {
                System.out.println("differing name for "+i+": "+a.getWireName(i)+" vs "+b.getWireName(i));
            }
            if (aConn.size() != bConn.size()) {
                System.out.println("differing connection size for "+i);
            }
            for (int j = 0; j < aConn.size(); j++) {
                final Wire aw = aConn.get(j);
                final Wire bw = bConn.get(j);
                if (!equalWire(aw, bw)) {
                    System.out.println("differing conn for "+a.getWireName(i)+" at "+j+": "+getWireContents(aw)+" vs "+getWireContents(bw));
                }
            }
        }
    }

    private static String getWireContents(Wire w) {
        return Arrays.stream(w.getNode().getAllWiresInNode()).map(Wire::getWireName).sorted().collect(Collectors.joining(", "));
    }

    private static boolean equalWire(Wire aw, Wire bw) {
        final int[] awires = Arrays.stream(aw.getNode().getAllWiresInNode()).mapToInt(Wire::getWireIndex).sorted().toArray();
        final int[] bwires = Arrays.stream(bw.getNode().getAllWiresInNode()).mapToInt(Wire::getWireIndex).sorted().toArray();
        return Arrays.equals(awires, bwires);
    }
}
