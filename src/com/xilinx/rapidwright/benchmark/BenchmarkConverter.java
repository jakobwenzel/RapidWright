package com.xilinx.rapidwright.benchmark;

import joptsimple.ValueConverter;

public class BenchmarkConverter implements ValueConverter<Benchmark> {
    private static BenchmarkConverter instance;
    public static BenchmarkConverter getInstance() {
        if (instance == null) {
            instance = new BenchmarkConverter();
        }
        return instance;
    }

    @Override
    public Benchmark convert(String s) {
        Benchmark res = BenchmarkFactory.getBenchmarkMap().get(s);
        if (res == null) {
            throw new RuntimeException("Benchmark not found: "+s);
        }
        return res;
    }

    @Override
    public Class<? extends Benchmark> valueType() {
        return Benchmark.class;
    }

    @Override
    public String valuePattern() {
        return null;
    }
}
