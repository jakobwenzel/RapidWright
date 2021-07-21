package com.xilinx.rapidwright.util.performance_evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimingResults {
    public final String constraintName;
    public final double worstSlack;
    public final double totalNegSlack;
    public final boolean constraintsMet;
    public final double clockPeriod;
    public final Path checkpoint;

    public TimingResults(String constraintName, double worstSlack, double totalNegSlack, boolean constraintsMet, double clockPeriod, Path checkpoint) {
        this.constraintName = constraintName;
        this.worstSlack = worstSlack;
        this.totalNegSlack = totalNegSlack;
        this.constraintsMet = constraintsMet;
        this.clockPeriod = clockPeriod;
        this.checkpoint = checkpoint;
    }

    public double clockFrequency() {
        return 1000/clockPeriod;
    }

    public double getMaxFrequency() {
        return 1000/getMinPeriod();
    }

    enum ParseState {
        BeforeMetLine,
        BeforeTable,
        BeforeSeparator,
        InContent,
        After
    }
    private static TimingResults parseContentLine(String line, boolean met, double clockPeriod, Path checkpoint) {
        String[] split = line.trim().split("\\s+");
        String worstHold = split[5];
        final TimingResults timingResults = new TimingResults(split[0], Double.parseDouble(split[1]), Double.parseDouble(split[2]), met, clockPeriod, checkpoint);
        if (worstHold.contains("-")) {
            System.err.println("Design has worst hold violation of " + worstHold + "ns. Other results: " + timingResults+". Check if there is a clock routing issue.");
        }
        return timingResults;
    }
    public static TimingResults parseTimingSummaryFile(Path file, double clockPeriod, Path checkpoint) throws IOException {
        return parseTimingSummaryFile(file, clockPeriod, checkpoint,null);
    }
    public static TimingResults parseTimingSummaryFile(Path file, double clockPeriod, Path checkpoint, String constraintName) throws IOException {
        ParseState ps = ParseState.BeforeMetLine;

        List<String> contentLine = new ArrayList<>();
        Boolean met = null;
        try (Stream<String> lines = Files.lines(file)) {
            LINE_LOOP:
            for (String line : (Iterable<String>) lines::iterator) {
                switch (ps) {
                    case BeforeMetLine:
                        if (line.startsWith("All user specified timing constraints are met.")) {
                            met = true;
                            ps = ParseState.BeforeTable;
                        } else if (line.startsWith("Timing constraints are not met.")) {
                            met = false;
                            ps = ParseState.BeforeTable;
                        }
                        break;
                    case BeforeTable:
                        if (line.contains("| Intra Clock Table")) {
                            ps = ParseState.BeforeSeparator;
                        }
                        break;
                    case BeforeSeparator:
                        if (line.contains("    ") && line.contains("----")) {
                            ps = ParseState.InContent;
                        }
                        break;
                    case InContent:
                        //After table?
                        if (line.trim().isEmpty()) {
                            break LINE_LOOP;
                        }
                        contentLine.add(line);
                        break;
                }

            }
        }
        if (met == null || contentLine.isEmpty()) {
            throw new RuntimeException("did not find timing resuult line in "+file);
        }
        try {
            boolean finalMet = met;
            final Map<String, TimingResults> resultsByConstraint = contentLine.stream().map(s -> parseContentLine(s, finalMet, clockPeriod, checkpoint))
                    .collect(Collectors.toMap(l -> l.constraintName, Function.identity()));
            if (constraintName == null) {
                if (resultsByConstraint.size() != 1) {
                    throw new RuntimeException("Got design with multiple clocks but no clock name was supplied. Results: " + resultsByConstraint.values());
                }
                return resultsByConstraint.values().iterator().next();
            }

            final TimingResults res = resultsByConstraint.get(constraintName);
            if (res == null) {
                throw new RuntimeException("no constraint of name "+constraintName+" exists in results: "+resultsByConstraint.values());
            }
            return res;
        } catch (RuntimeException e) {
            throw new RuntimeException("could not parse timing result line in "+file,e);
        }
    }

    @Override
    public String toString() {
        return "TimingResults{" +
                "constraintName="+constraintName+
                ", worstSlack=" + worstSlack +
                ", totalNegSlack=" + totalNegSlack +
                ", constraintsMet=" + constraintsMet +
                ", clockPeriod=" + clockPeriod +
                '}';
    }

    public double getMinPeriod() {
        return clockPeriod - worstSlack;
    }
}
