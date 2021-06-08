package com.xilinx.rapidwright.benchmark;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.examples.PicoBlazeArray;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.Pair;
import com.xilinx.rapidwright.util.performance_evaluation.PerformanceEvaluation;
import com.xilinx.rapidwright.util.performance_evaluation.TimingResults;

public class PicoBlazeArrayBenchmark extends Benchmark {

    public static final String DEVICE_NAME = "xcvu3p-ffvc1517-2-i";

    protected PicoBlazeArrayBenchmark() {
        super("PicoBlazeArray");
    }

    @Override
    public String getType() {
        return getName();
    }

    @Override
    public String getId() {
        return getName();
    }

    @Override
    public Pair<Design, Collection<ModuleImplsInstance>> createModulesDesign(CodePerfTracker t, Path cache) {
        PicoBlazeArray.PicoBlazeArrayCreator<ModuleImplsInstance> picoBlazeArrayCreator = PicoBlazeArray.makeImplsCreator(null);


        Path srcDir = PathConfig.getPicoblazeArray();

        Design design = picoBlazeArrayCreator.createDesign(srcDir.toFile(), DEVICE_NAME, t);
        return new Pair<>(design, picoBlazeArrayCreator.getInstances());
    }

    @Override
    public Optional<String> getUnavailableReason() {
        return Optional.empty();
    }

    @Override
    public double getEstimatedMaxFreqMhz() {
        return 330;
    }

    @Override
    public RegularRun getRegularRun() {
        return new RegularRun() {
            @Override
            public String getId() {
                return PicoBlazeArrayBenchmark.this.getId()+"/optimal";
            }

            @Override
            public String getRunGroup() {
                return getName();
            }

            @Override
            public Pair<TimingResults, TimingResults> doRun(CodePerfTracker t, Path workDirRoot, boolean reuseMaxFreq, boolean evalOnly) {
                PicoBlazeArray.PicoBlazeArrayCreator<ModuleInst> picoBlazeArrayCreator = PicoBlazeArray.makeModuleCreator(null);


                Path srcDir = PathConfig.getPicoblazeArray();
                String deviceName = "xcvu3p-ffvc1517-2-i";

                Design design = picoBlazeArrayCreator.createDesign(srcDir.toFile(), deviceName, t);

                Path workDir = workDirRoot.resolve(getId());
                //return new Pair<>(new TimingResults(0,0,true, 0), new TimingResults(0,0,false, 0));
                return PerformanceEvaluation.getMaxFrequency(design, BenchmarkRun.guessClockName(design), workDir, reuseMaxFreq, 380);
            }
        };
    }

    @Override
    public String getRunGroup() {
        return getName();
    }

    @Override
    public String getClockName() {
        return "clkin";
    }

    private Integer moduleCount = null;
    @Override
    public Pair<Integer, Integer> getModuleCounts() {
        if (moduleCount == null) {
            final Pair<Design, Collection<ModuleImplsInstance>> modulesDesign = createModulesDesign(new CodePerfTracker("temp"), null);
            moduleCount = modulesDesign.getSecond().size();
        }
        return new Pair<>(1,moduleCount);
    }

    @Override
    public Stream<Pair<Path,Path>> getModuleDcps(Path workDirRoot) {

        final String PBLOCK_DCP_PREFIX = "pblock";

        FilenameFilter ff = FileTools.getFilenameFilter(PBLOCK_DCP_PREFIX+"[0-9]+.dcp");
        final File srcDir = PathConfig.getPicoblazeArray().toFile();
        int implementationCount = srcDir.list(ff).length;


        return IntStream.range(0, implementationCount).mapToObj(i-> new Pair<>(Paths.get(srcDir + File.separator + PBLOCK_DCP_PREFIX + i + ".dcp"), null));
    }
}
