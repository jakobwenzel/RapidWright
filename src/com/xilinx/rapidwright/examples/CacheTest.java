package com.xilinx.rapidwright.examples;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleCache;
import com.xilinx.rapidwright.design.ModuleImpls;
import com.xilinx.rapidwright.design.Port;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.FileTools;

public class CacheTest {
    private static final String PBLOCK_DCP_PREFIX = "pblock";
    private static ModuleImpls readImpls(File srcDir) {
        FilenameFilter ff = FileTools.getFilenameFilter(PBLOCK_DCP_PREFIX+"[0-9]+.dcp");
        int implementationCount = srcDir.list(ff).length;
        ModuleImpls result = new ModuleImpls();
        for(int i=0; i < implementationCount; i++){
            String dcpName = srcDir + File.separator + PBLOCK_DCP_PREFIX+i+".dcp";
            String metaName = srcDir + File.separator + PBLOCK_DCP_PREFIX+i+"_"+i+"_metadata.txt";
            Module mod = new Module(Design.readCheckpoint(dcpName,CodePerfTracker.SILENT), metaName);
            result.add(mod);
        }
        return result;
    }
    public static void main(String[] args) throws IOException {



        ModuleImpls impls = readImpls(new File(args[0]));

        String tempFile = Files.createTempFile("moduleCache", ".dat").toString();
        ModuleCache.saveToCompactFile(impls, tempFile);
        ModuleImpls reread = ModuleCache.readFromCompactFile(tempFile, impls.getNetlist());

        verifyEqual(impls, reread);


    }

    static String portsToString(Module mod) {
        return mod.getPorts().stream()
                .sorted(Comparator.comparing(Port::getName))
                .map(p -> {
                    String pins = p.getSitePinInsts().stream().map(Object::toString).collect(Collectors.joining());
                    return p.getName() + ": " + pins;
                }).collect(Collectors.joining("\n"));
    }

    private static void verifyEqual(ModuleImpls impls, ModuleImpls reread) {
        if (impls.size() != reread.size()) {
            throw new RuntimeException("size different");
        }
        for (int i = 0; i < impls.size(); i++) {
            Module a = impls.get(i);
            Module b = reread.get(i);

            String aPorts = portsToString(a);
            String bPorts = portsToString(b);

            if (!aPorts.equals(bPorts)) {
                throw new RuntimeException("ports differ:\n"+aPorts+"\nvs\n"+bPorts);
            }

        }
    }
}
