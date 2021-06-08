package com.xilinx.rapidwright.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.util.performance_evaluation.PerformanceEvaluation;
import com.xilinx.rapidwright.util.performance_evaluation.PerformanceEvaluation.RouteRun;
import com.xilinx.rapidwright.util.performance_evaluation.TimingResults;

public class VerilogStitcherRegularRun extends RegularRun {
    private final Path benchmarkRoot;

    public VerilogStitcherRegularRun(Path benchmarkRoot) {
        this.benchmarkRoot = benchmarkRoot;
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

        PerformanceEvaluation eval = new PerformanceEvaluation(workDir, null, reuseMaxFreq, null) {
            @Override
            protected RouteRun getRouteRun(double clockPeriod, Path jobDir) {
                return new RosettaRun(jobDir, clockPeriod, reuseMaxFreq, benchmarkRoot.getFileName().toString());
            }
        };

        return eval.run(240);
    }

    protected static class RosettaRun extends RouteRun {
        private final String benchmarkName;

        @Override
        public void createRouteScript() throws IOException {
            try {
                Files.list(PathConfig.getRosettaBenchmark(benchmarkName))
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
            final List<String> newContent = Files.lines(runScript)
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

            final Path constraints = getConstraintsName();
            final List<String> newConstraintContent = Files.lines(constraints)
                    .map(l -> {
                        if (l.contains("create_clock")) {
                            return "create_clock -name ap_clk -period "+clockPeriod+" -waveform {0.000 "+(clockPeriod/2)+"} [get_ports ap_clk]";
                        }
                        return l;
                    }).collect(Collectors.toList());
            Files.write(constraints, newConstraintContent);
        }

        private Path getConstraintsName() {
            try {
                final List<Path> result = Files.list(jobDir).filter(p -> p.toString().endsWith(".xdc"))
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
            try {
                final List<Path> result = Files.list(jobDir.resolve("report")).filter(p -> p.toString().endsWith(end))
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
