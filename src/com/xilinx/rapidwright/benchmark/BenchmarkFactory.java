package com.xilinx.rapidwright.benchmark;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BenchmarkFactory {

    private static Map<String, Benchmark> benchmarks;

    private static Stream<? extends Benchmark> streamBenchmarks() {
        return Stream.concat(Stream.of(new PicoBlazeArrayBenchmark()), VerilogStitcherBenchmark.getBenchmarks());
    }

    public static Map<String, Benchmark> getBenchmarkMap() {
        if (benchmarks == null) {
            try (Stream<? extends Benchmark> stream = streamBenchmarks()) {
                benchmarks = stream.collect(Collectors.toMap(Benchmark::getId, Function.identity()));
            }
        }
        return benchmarks;
    }
}
