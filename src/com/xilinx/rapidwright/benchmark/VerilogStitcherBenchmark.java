package com.xilinx.rapidwright.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPropertyValue;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.ipi.AbstractBlockStitcher;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.verilogModules.VerilogStitcher;

public class VerilogStitcherBenchmark extends Benchmark {
    public final Path dir;
    private final Path benchmarkRoot;
    private final String partName = "xcvu065-ffvc1517-3-e";

    public VerilogStitcherBenchmark(String name, Path dir, Path benchmarkRoot) {
        super(name);
        this.dir = dir;
        this.benchmarkRoot = benchmarkRoot;
    }

    public static Stream<VerilogStitcherBenchmark> getBenchmarks() {
        Path baseDir = PathConfig.getYomoRosetta();

        try {
            return Files.list(baseDir).filter(Files::isDirectory).flatMap(VerilogStitcherBenchmark::listBenchmarkFolder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<VerilogStitcherBenchmark> listBenchmarkFolder(Path dir) {
        try {
            Path partitionerDir = dir.resolve("partitioner");
            Stream<Path> partitioners = Files.isDirectory(partitionerDir) ? Files.list(partitionerDir) : Stream.empty();
            Stream<Path> subdirs = Stream.concat(partitioners, Stream.of(dir.resolve("rwExport")));

            return subdirs.filter(Files::isDirectory).map(d -> new VerilogStitcherBenchmark(dir.getFileName() + "/" + d.getFileName(), d, dir));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public String getType() {
        return "verilogStitcher";
    }

    @Override
    public Pair<Design, Collection<ModuleImplsInstance>> createModulesDesign(CodePerfTracker t, Path cache) {
        final Optional<String> unavailableReason = getUnavailableReason();
        if (unavailableReason.isPresent()) {
            throw new RuntimeException("unavailable: "+unavailableReason.get());
        }

        System.out.println("Reading design from "+dir);
        VerilogStitcher stitcher = new VerilogStitcher(cache, dir.resolve("design.edf"), partName);
        AbstractBlockStitcher.DesignData designData = stitcher.buildDesign(t, true);
        return new Pair<>(designData.stitched, designData.moduleInsts.values());
    }


    @Override
    public Optional<String> getUnavailableReason() {
        try {
            Path errorTxt = dir.resolve("error.txt");
            if (Files.exists(errorTxt)) {
                String text = String.join("\n", Files.readAllLines(errorTxt));
                if (text.trim().equals("RAPIDWRIGHT_PATH not set")) {
                    return Optional.empty();
                }
                return Optional.of(text);
            }
            if (Files.list(dir).count() < 2) {
                return Optional.of("Unknown");
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public double getEstimatedMaxFreqMhz() {
        return 125;
    }

    @Override
    public RegularRun getRegularRun() {
        return new VerilogStitcherRegularRun(benchmarkRoot);
    }

    @Override
    public String getRunGroup() {
        return "verilogStitcher/"+benchmarkRoot.getFileName();
    }

    @Override
    public String getClockName() {
        return "ap_clk";
    }



    private Pair<Set<String>, Integer> hashesCached = null;
    public Pair<Set<String>, Integer> getModuleHashes() {
        if (hashesCached == null) {
            final EDIFNetlist edifNetlist = EDIFTools.readEdifFile(dir.resolve("design.edf"));

            int numModules = 0;
            Set<String> cacheIds = new HashSet<>();

            for (EDIFCellInst cellInst : edifNetlist.getTopCell().getCellInsts()) {
                final EDIFPropertyValue hash = cellInst.getCellType().getProperty("hash");
                if (hash != null) {
                    numModules++;
                    cacheIds.add(hash.getValue());
                }
            }

            hashesCached = new Pair<>(cacheIds, numModules);
        }
        return hashesCached;
    }

    @Override
    public Pair<Integer, Integer> getModuleCounts() {
        final Pair<Set<String>, Integer> moduleHashes = getModuleHashes();

        return new Pair<>(moduleHashes.getFirst().size(), moduleHashes.getSecond());
    }

    @Override
    public Stream<Pair<Path, Path>> getModuleDcps(Path workDirRoot) {
        //TODO use normal cache once we have locking
        Path cache = workDirRoot.resolve(getId()).resolve("cache");
        return getModuleHashes().getFirst().stream().map(hash-> {
            final Path dir = cache.resolve(partName + "_" + hash);
            return new Pair<>(dir.resolve("design_0_routed.dcp"), dir.resolve("design_routed.edf"));
        });
    }
}
