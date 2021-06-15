package com.xilinx.rapidwright.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.placer.blockplacer.BlockPlacer2Impls;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.Pair;

public class EvalPlacerOverlapSize {
    public static void main(String[] args) throws IOException {
        /*if (args.length==1) {
            run(Integer.parseInt(args[0]));
        }*/
        if (args.length>0 && args[0].equals("--results")) {
            eval();
        } else {

            for (int i=1;i<100;i++) {
                run(i);
            }
            for (int i=100;i<650;i+=10) {
                run(i);
            }
        }
    }

    private static void eval() throws IOException {
        final Device device = Device.getDevice(PicoBlazeArrayBenchmark.DEVICE_NAME);
        System.out.println(device.getColumns()+"x"+device.getRows());
        try (Stream<Path> list = Files.list(Paths.get("."))) {
            list.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        final int i = Integer.parseInt(p.getFileName().toString().replaceAll(".json", "").replaceAll("result_", ""));
                        final List<BenchmarkResult.RuntimeLog> runtimes = BenchmarkResult.fromJson(null, p).runtimes;
                        return new Pair<>(i, runtimes);
                    }).sorted(Comparator.comparing(Pair::getFirst))
                    .forEach(p -> {
                        int i = p.getFirst();
                        final BenchmarkResult.RuntimeLog placer = p.getSecond().stream().filter(l -> l.name.equals("Place Design")).collect(StreamTools.exactlyOne());
                        final BenchmarkResult.RuntimeLog total = p.getSecond().stream().filter(l -> l.name.equals("*Total*")).collect(StreamTools.exactlyOne());
                        System.out.println(i + "\t" + placer.runtime * 1E-9 + "\t" + placer.memory * 1E-6 + "\t" + total.runtime * 1E-9 + "\t" + total.memory * 1E-6);
                    });
        }
    }

    private static void run(int size) {
        final Path output = Paths.get(".").resolve("result_" + size + ".json");
        if (Files.exists(output)) {
            return;
        }
        final PicoBlazeArrayBenchmark picoBlazeArrayBenchmark = new PicoBlazeArrayBenchmark();
        final CodePerfTracker t = new CodePerfTracker("Benchmark");
        t.useGCToTrackMemory(true);
        final Pair<Design, Collection<ModuleImplsInstance>> pair = picoBlazeArrayBenchmark.createModulesDesign(t, null);


        t.stop().start("Place Design");
        final BlockPlacer2Impls placer = new BlockPlacer2Impls(pair.getFirst(), pair.getSecond(), true, null, size);
        placer.placeDesign(false);
        t.stop();
        t.printSummary();

        final BenchmarkResult benchmarkResult = new BenchmarkResult(null);
        benchmarkResult.runtimes = BenchmarkResult.RuntimeLog.fromCodePerfTracker(t);
        benchmarkResult.toJson(output);


    }
}
