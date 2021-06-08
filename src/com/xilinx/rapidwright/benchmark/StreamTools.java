package com.xilinx.rapidwright.benchmark;

import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class StreamTools {
    public static <T> Collector<T, ?, T> exactlyOne() {
        return Collectors.collectingAndThen(Collectors.toList(), l -> {
            if (l.size() == 1) {
                return l.get(0);
            }
            throw new IllegalStateException("Expected stream with one item, but got: "+l);
        });
    }
    public static <T> Collector<T, ?, Optional<T>> atMostOne() {
        return Collectors.collectingAndThen(Collectors.toList(), l -> {
            if (l.size() == 1) {
                return Optional.of(l.get(0));
            }
            if (l.size() == 0) {
                return Optional.empty();
            }
            throw new IllegalStateException("Expected stream with zero or one item, but got: "+l);
        });
    }
}
