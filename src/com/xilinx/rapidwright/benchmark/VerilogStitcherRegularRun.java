package com.xilinx.rapidwright.benchmark;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.util.performance_evaluation.PerformanceEvaluation;
import com.xilinx.rapidwright.util.performance_evaluation.PerformanceEvaluation.RouteRun;
import com.xilinx.rapidwright.util.performance_evaluation.TimingResults;

public class VerilogStitcherRegularRun extends RegularRun {
    private final Path benchmarkRoot;
    private final boolean isRosetta;
    private final String top;
    private final String part;
    private final String clkPortName;

    public VerilogStitcherRegularRun(Path benchmarkRoot, boolean isRosetta, String top, String part, String clkPortName) {
        this.benchmarkRoot = benchmarkRoot;
        this.isRosetta = isRosetta;
        this.top = top;
        this.part = part;
        this.clkPortName = clkPortName;
    }

    @Override
    public String getId() {
        return "verilogStitcher/"+benchmarkRoot.getFileName()+"/regular";
    }

    @Override
    public String getRunGroup() {
        return "verilogStitcher/"+benchmarkRoot.getFileName();
    }

    @Override
    public Pair<TimingResults, TimingResults> doRun(CodePerfTracker t, Path workDirRoot, boolean reuseMaxFreq, boolean evalOnly) {


        Path workDir = workDirRoot.resolve(getId()).resolve("regular");
        try {
            Files.createDirectories(workDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        PerformanceEvaluation eval = new PerformanceEvaluation(workDir, null, reuseMaxFreq, clkPortName) {
            @Override
            protected RouteRun getRouteRun(double clockPeriod, Path jobDir) {
                if (isRosetta) {
                    return new RosettaRun(jobDir, clockPeriod, reuseMaxFreq, benchmarkRoot.getFileName().toString());
                } else {
                    return new VerilogRun(jobDir, clockPeriod, reuseMaxFreq, benchmarkRoot, clkPortName, top, part);
                }
            }
        };

        return eval.run(240);
    }

    protected static class VerilogRun extends RouteRun {
        private final Path benchmarkRoot;
        private final String top;
        private final String part;

        public VerilogRun(Path jobDir, double clockPeriod, boolean reuseExistingResults, Path benchmarkRoot, String clkPortName, String top, String part) {
            super(jobDir, clockPeriod, reuseExistingResults, null, clkPortName);

            this.benchmarkRoot = benchmarkRoot;
            this.top = top;
            this.part = part;
        }

        @Override
        public void createRouteScript() throws IOException {
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(getScriptName()))) {
                Path src = benchmarkRoot.resolve("keephierarchy").resolve("AST").resolve("design.v");

                pw.println("read_verilog "+src);
                pw.println("synth_design -top "+top+" -part "+part+" -flatten rebuilt");
                createClock(pw);
                pw.println("opt_design");
                pw.println("power_opt_design");
                pw.println("place_design");
                pw.println("phys_opt_design");
                routeAndSave(pw);
            }
        }
    }

    protected static class RosettaRun extends RouteRun {
        private final String benchmarkName;

        @Override
        public void createRouteScript() throws IOException {

            try (final Stream<Path> list = Files.list(PathConfig.getRosettaBenchmark(benchmarkName));){
                list
                        .filter(f->!Files.isDirectory(f))
                        .forEach(f -> {
                            try {
                                Files.copy(f, jobDir.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            final Path runScript = getScriptName();
            try (Stream<String> lines = Files.lines(runScript)) {
                final List<String> newContent = lines
                        .map(l -> {
                            if (l.contains("set target_clk_period_ns")) {
                                return "set target_clk_period_ns \"" + clockPeriod + '"';
                            }
                            if (l.contains("set ip_repo_path")) {
                                return "set ip_repo_path {" + PathConfig.getRosettaBenchmark(benchmarkName).getParent().resolve("ip") + '}';
                            }
                            return l;
                        }).collect(Collectors.toList());
                Files.write(runScript, newContent);
            }

            final Path constraints = getConstraintsName();
            try (Stream<String> lines = Files.lines(constraints)) {
                final List<String> newConstraintContent = lines
                        .map(l -> {
                            if (l.contains("create_clock")) {
                                return "create_clock -name ap_clk -period " + clockPeriod + " -waveform {0.000 " + (clockPeriod / 2) + "} [get_ports ap_clk]";
                            }
                            return l;
                        }).collect(Collectors.toList());
                Files.write(constraints, newConstraintContent);
            }
        }

        private Path getConstraintsName() {
            try (final Stream<Path> list = Files.list(jobDir)){
                final List<Path> result = list.filter(p -> p.toString().endsWith(".xdc"))
                        .collect(Collectors.toList());
                if (result.size() != 1) {
                    throw new RuntimeException("Not one result for '*.xdc' but: " + result);
                }
                return result.get(0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public RosettaRun(Path jobDir, double clockPeriod, boolean reuseExistingResults, String benchmarkName) {
            super(jobDir, clockPeriod, reuseExistingResults, null, null);
            this.benchmarkName = benchmarkName;
        }

        @Override
        protected Path getScriptName() {
            return jobDir.resolve("run_vivado.tcl");
        }

        @Override
        protected Path getRoutedDcp() {
            return jobDir.resolve("project.runs/impl_1/bd_0_wrapper_routed.dcp");
        }

        private Path getReport(String end) {
            try (final Stream<Path> report = Files.list(jobDir.resolve("report"))) {
                final List<Path> result = report.filter(p -> p.toString().endsWith(end))
                        .collect(Collectors.toList());
                if (result.size() != 1) {
                    throw new RuntimeException("Not one result for '" + end + "' but: " + result);
                }
                return result.get(0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        protected Path getTimingReportPath() {
            return getReport("_timing_paths_routed.rpt");
        }

        @Override
        protected Path getTimingSummaryReportPath() {
            return getReport("_timing_routed.rpt");
        }
    }
}
