package com.xilinx.rapidwright.benchmark;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathConfig {
    public static Path getYomoRosetta() {
        final Path resolve = getRwrWorkdir();
        return resolve.resolve("yomo").resolve("rosetta");
    }

    private static Path getRwrWorkdir() {
        return Paths.get(System.getenv("HOME")).resolve("rwWorkdir");
    }

    public static Path getPicoblazeArray() {
        return getRwrWorkdir().resolve("picoBlazeArray");
    }

    public static Path getRosettaBenchmark(String benchmarkName) {
        return Paths.get(System.getenv("HOME")).resolve("git").resolve("run_rosetta")
                .resolve(benchmarkName).resolve("solution").resolve("impl").resolve("verilog");
    }
}
