package com.xilinx.rapidwright.summer20;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPortInst;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.util.Pair;

public class AnalyzeConnectivity {
    public static void main(String[] args) {
        final EDIFNetlist edifNetlist = EDIFTools.readEdifFile("USS_opt_u250.edf");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Paths.get("connectivity.dot")))) {
            dumpConnectivity(edifNetlist.getTopCell(), pw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private static String getGroup(EDIFCellInst cellInst) {
        if (cellInst == null) {
            return "ROOT";
        }
        if (cellInst.getName().startsWith("tile")) {
            return cellInst.getName();
        }
        return "shell";
    }

    private static void dumpConnectivity(EDIFCell cell, PrintWriter pw) {
        Map<Pair<String, String>, Long> connectivity = analyzeConnectivity(cell);
        pw.println("graph \"G\"{");
        //pw.println("splines=\"line\";");

        Map<String, String> nodes = declareNodes(connectivity, pw);

        connectivity.forEach((conn, count) -> {
            List<String> attributes = new ArrayList<>();
            String label = "label=\""+count+"\"";
            if (conn.getFirst().startsWith("tile") && conn.getSecond().startsWith("tile")) {

                if (getTilePos(conn.getFirst()).getFirst().equals(getTilePos(conn.getSecond()).getFirst())) {
                    attributes.add("weight=1000");
                } else {
                }
            } else {
                attributes.add(label);
            }

            pw.println(nodes.get(conn.getFirst())+"--"+nodes.get(conn.getSecond())+"["+attributes.stream().collect(Collectors.joining(", "))+"]"+";");
        });
        pw.println("}");
    }

    private static String escapeStr(String name) {
        return name.codePoints()
                .map(c -> {
                    if (!Character.isLetterOrDigit(c)) {
                        return '_';
                    }
                    return c;
                })
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /**
     * Pair&lt;X,Y&gt;
     */
    private static Pair<Integer, Integer> getTilePos(String name) {
        final Pattern pattern = Pattern.compile("tile([0-9])_([0-9])");
        final Matcher matcher = pattern.matcher(name);
        if (!matcher.matches()) {
            throw new RuntimeException("invalname "+name);
        }
        return new Pair<>(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }


    private static Map<String, String> declareNodes(Map<Pair<String, String>, Long> connectivity, PrintWriter pw) {
        final Map<String, String> result = connectivity.keySet().stream().flatMap(p -> Stream.of(p.getFirst(), p.getSecond()))
                .distinct().collect(Collectors.toMap(Function.identity(), AnalyzeConnectivity::escapeStr));

        result.forEach((g,escaped) -> pw.println(escaped+"[label=\""+g+"\"];"));

        final Map<Integer, List<String>> byX = result.keySet().stream().filter(s -> s.startsWith("tile"))
                .sorted()
                .collect(Collectors.groupingBy(s -> getTilePos(s).getFirst(), Collectors.mapping(result::get,Collectors.toList())));
        final Map<Integer, List<String>> byY = result.keySet().stream().filter(s -> s.startsWith("tile"))
                .sorted()
                .collect(Collectors.groupingBy(s -> getTilePos(s).getSecond(), Collectors.mapping(result::get,Collectors.toList())));

        byY.forEach((y, tiles) -> {
            pw.println("{rank=same;");
            tiles.forEach(s->pw.println(s+";"));
            pw.println("}");
        });


        /*byX.forEach((x, tiles) -> {
            for (int y = 0; y<tiles.size()-1;y++) {
                pw.println(tiles.get(y)+"--"+tiles.get(y+1)+"[weight=10000, color=red];");
            }
        });*/

        return result;
    }

    private static Map<Pair<String, String>, Long> analyzeConnectivity(EDIFCell cell) {
        return cell.getNets().stream()
                .flatMap(net-> {
                    /*if (net.getName().startsWith("clk_")) {
                        return Stream.empty();
                    }*/
                    final List<EDIFPortInst> sources = net.getSourcePortInsts(true);
                    if (sources.size()!=1) {
                        //TODO we don't support static nets right now?
                        throw new RuntimeException("not one source for "+net);
                    }
                    final EDIFPortInst sourcePort = sources.get(0);
                    String src = getGroup(sourcePort.getCellInst());

                    final Map<String, Long> byCell = net.getPortInsts().stream()
                            .filter(pi -> pi != sourcePort)
                            .collect(Collectors.groupingBy(pi -> getGroup(pi.getCellInst()), Collectors.counting()));
                    long allTileConn = getAllTileConn(byCell);
                    decrementAllTiles(byCell, allTileConn);
                    byCell.put("ALL TILES", allTileConn);

                    return byCell.entrySet().stream().flatMap(entry -> {
                        Pair<String, String> pair = new Pair<>(src, entry.getKey());
                        return IntStream.range(0, Math.toIntExact(entry.getValue())).mapToObj(x->pair);
                    }).map(AnalyzeConnectivity::orientPair);

                })
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static long getAllTileConn(Map<String, Long> byCell) {
        Stream<String> allTileNames = getAllTileNames();

        long min = Long.MAX_VALUE;
        for (String name: (Iterable<String>)allTileNames::iterator) {

            final Long count = byCell.get(name);
            if (count == null) {
                return 0;
            }
            if (count < min) {
                min = count;
            }
        }
        if (min == Long.MAX_VALUE) {
            throw new IllegalStateException("should have already returned?");
        }
        return min;
    }

    private static void decrementAllTiles(Map<String, Long> byCell, long amount) {
        if (amount == 0) {
            return;
        }
        Stream<String> allTileNames = getAllTileNames();

        for (String name: (Iterable<String>)allTileNames::iterator) {

            final Long count = byCell.get(name);
            if (count == null) {
                throw new RuntimeException("entry not present? "+name);
            }
            byCell.put(name, count-amount);
        }
    }

    private static Stream<String> getAllTileNames() {
        Stream<String> allTileNames = IntStream.rangeClosed(1,4).boxed().flatMap(x->IntStream.rangeClosed(1,4).mapToObj(y->"tile"+x+"_"+y));
        return allTileNames;
    }

    private static Pair<String, String> orientPair(Pair<String, String> p) {
        if (p.getFirst().compareTo(p.getSecond()) <= 0) {
            return p;
        }
        return new Pair<>(p.getSecond(), p.getFirst());
    }
}
