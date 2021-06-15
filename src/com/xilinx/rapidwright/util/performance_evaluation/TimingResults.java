package com.xilinx.rapidwright.util.performance_evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class TimingResults {
    public final double worstSlack;
    public final double totalNegSlack;
    public final boolean constraintsMet;
    public final double clockPeriod;

    public TimingResults(double worstSlack, double totalNegSlack, boolean constraintsMet, double clockPeriod) {
        this.worstSlack = worstSlack;
        this.totalNegSlack = totalNegSlack;
        this.constraintsMet = constraintsMet;
        this.clockPeriod = clockPeriod;
    }

    public double clockFrequency() {
        return 1000/clockPeriod;
    }

    public double getMaxFrequency() {
        return 1000/getMinPeriod();
    }

    enum ParseState {
        BeforeTable,
        BeforeSeparator,
        InContent,
        BeforeMetLine,
        After
    }
    private static TimingResults parseContentLine(String line, boolean met, double clockPeriod) {
        String[] split = line.trim().split("\\s+");
        String worstHold = split[4];
        final TimingResults timingResults = new TimingResults(Double.parseDouble(split[0]), Double.parseDouble(split[1]), met, clockPeriod);
        if (worstHold.contains("-")) {
            throw new RuntimeException("Design has worst hold violation of " + worstHold + "ns. Other results: " + timingResults);
        }
        return timingResults;
    }
    public static TimingResults parseTimingSummaryFile(Path file, double clockPeriod) throws IOException {
        ParseState ps = ParseState.BeforeTable;

        String contentLine = null;
        Boolean met = null;
        try (Stream<String> lines = Files.lines(file)) {
            LINE_LOOP:
            for (String line : (Iterable<String>) lines::iterator) {
                switch (ps) {
                    case BeforeTable:
                        if (line.contains("| Design Timing Summary")) {
                            ps = ParseState.BeforeSeparator;
                        }
                        break;
                    case BeforeSeparator:
                        if (line.contains("    ") && line.contains("----")) {
                            ps = ParseState.InContent;
                        }
                        break;
                    case InContent:
                        contentLine = line;
                        ps = ParseState.BeforeMetLine;
                        break;
                    case BeforeMetLine:
                        if (line.startsWith("All user specified timing constraints are met.")) {
                            met = true;
                            break LINE_LOOP;
                        } else if (line.startsWith("Timing constraints are not met.")) {
                            met = false;
                            break LINE_LOOP;
                        }
                        break;
                }

            }
        }
        if (contentLine == null || met == null) {
            throw new RuntimeException("did not find timing resuult line in "+file);
        }
        try {
            return parseContentLine(contentLine, met, clockPeriod);
        } catch (RuntimeException e) {
            throw new RuntimeException("could not parse timing result line in "+file,e);
        }
    }

    @Override
    public String toString() {
        return "TimingResults{" +
                "worstSlack=" + worstSlack +
                ", totalNegSlack=" + totalNegSlack +
                ", constraintsMet=" + constraintsMet +
                ", clockPeriod=" + clockPeriod +
                '}';
    }

    public double getMinPeriod() {
        return clockPeriod - worstSlack;
    }
}
