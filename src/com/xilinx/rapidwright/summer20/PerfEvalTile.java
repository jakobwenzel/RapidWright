package com.xilinx.rapidwright.summer20;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xilinx.rapidwright.util.performance_evaluation.PerformanceEvaluation;
import com.xilinx.rapidwright.util.performance_evaluation.TimingResults;

public class PerfEvalTile {
    public static void main(String[] args) throws IOException {
        Path workDir = Paths.get(".").toAbsolutePath();
        Path dcp = Paths.get(args[0]).toAbsolutePath();
        Path constraints = Paths.get(args[1]).toAbsolutePath();
        final List<String> constraintsList;
        try (Stream<String> lines = Files.lines(constraints)) {
            constraintsList = lines
                    .filter(l->!l.contains("create_clock") || !l.contains("TS_clk_line"))
                    .collect(Collectors.toList());

        }
        PerformanceEvaluation perfEval = new PerformanceEvaluation(workDir, dcp, true, "clk_line") {
            @Override
            protected RouteRun getRouteRun(double clockPeriod, Path jobDir) {
                return new RouteRun(jobDir, clockPeriod, reuseExistingResults, dcp, clkPortName) {
                    @Override
                    public void createRouteScript() throws IOException {

                        final Path jobConstraints = jobDir.resolve("constraints.xdc");
                        Files.write(jobConstraints, constraintsList);

                        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(getScriptName()))) {
                            pw.println("create_project -force tile_project -part xcu250-figd2104-2L-e");
                            pw.println("read_checkpoint "+dcp);
                            pw.println("link_design -top USS");
                            //pw.println("open_checkpoint " + dcp);
                            createClock(pw, "TS_clk_line");
                            pw.println("delete_pblocks -hier *");
                            //constraintsList.forEach(pw::println);
                            pw.println("read_xdc "+jobConstraints);
                            pw.println("place_design -timing_summary");
                            pw.println("set_param hd.routingContainmentAreaExpansion false");
                            //pw.println("route_design -timing_summary");
                            //pw.println("write_checkpoint -force postroute.dcp");
                            routeAndSave(pw);
                        }
                    }

                    @Override
                    protected TimingResults getResults() {
                        if (results ==null) {
                            try {
                                results = TimingResults.parseTimingSummaryFile(getTimingSummaryReportPath(), clockPeriod, "TS_clk_line");
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                        return results;
                    }
                };
            }
        };
        perfEval.run(434);
    }
}
