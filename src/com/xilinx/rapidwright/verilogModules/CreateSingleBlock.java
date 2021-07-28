package com.xilinx.rapidwright.verilogModules;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.ipi.BlockCreator;
import com.xilinx.rapidwright.ipi.IPCore;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class CreateSingleBlock {
    public static void main(String[] args) throws FileNotFoundException {

        OptionParser optionParser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> verilogFileOption = optionParser.accepts("in", "Input Verilog file").withRequiredArg().required();
        ArgumentAcceptingOptionSpec<String> nameOption = optionParser.accepts("name", "Topmodule name").withRequiredArg().required();
        ArgumentAcceptingOptionSpec<String> cacheOption = optionParser.accepts("cache", "Cache Directory").withRequiredArg().required();
        ArgumentAcceptingOptionSpec<String> partOption = optionParser.accepts("part", "FPGA Part").withRequiredArg().required();


        OptionSet options;
        try {
            options = optionParser.parse(args);
        } catch (RuntimeException e) {
            try {
                optionParser.printHelpOn(System.out);
            } catch (IOException ioException) {
                throw new UncheckedIOException(ioException);
            }
            throw e;
        }

        Path verilogFile = Paths.get(options.valueOf(verilogFileOption));
        if (!Files.exists(verilogFile)) {
            throw new FileNotFoundException("Input Verilog does not exist at "+verilogFile);
        }
        Path cache = Paths.get(options.valueOf(cacheOption));

        String part = options.valueOf(partOption);
        String name = options.valueOf(nameOption);


        IPCore core = new IPCore(name, "manual", name+"_inst", verilogFile);
        Map<String, IPCore> ipNames = new HashMap<>();
        ipNames.put(core.getInstName(), core);
        BlockCreatorVerilog.synthBlocks(ipNames, cache.toFile().getAbsolutePath(), part);
        BlockCreator.implementBlocks(ipNames, cache.toFile().getAbsolutePath(), null, Device.getDevice(part));

        Path dir = cache.resolve("manual");


        EDIFNetlist netlist = EDIFTools.readEdifFile(dir.resolve("design_routed.edf").toString());
        BlockCreator.createBlock(dir.resolve("design_0_routed.dcp").toString(), null, netlist, 1, name+"_inst");
    }
}
