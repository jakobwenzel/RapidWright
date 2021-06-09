package com.xilinx.rapidwright.verilogModules;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.NetType;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.Unisim;
import com.xilinx.rapidwright.design.blocks.ImplGuide;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFHierCellInst;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import com.xilinx.rapidwright.edif.EDIFName;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPortInst;
import com.xilinx.rapidwright.edif.EDIFPropertyValue;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.examples.SLRCrosserGenerator;
import com.xilinx.rapidwright.ipi.AbstractBlockStitcher;
import com.xilinx.rapidwright.ipi.BlockCreator;
import com.xilinx.rapidwright.ipi.ClockConstraint;
import com.xilinx.rapidwright.ipi.IPCore;
import com.xilinx.rapidwright.ipi.PackagePinConstraint;
import com.xilinx.rapidwright.ipi.XDCConstraints;
import com.xilinx.rapidwright.util.FileTools;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class VerilogStitcher extends AbstractBlockStitcher {

    public VerilogStitcher(Path cacheDir, Path edifFile, String partName) {
        super(cacheDir, edifFile);
        this.partName = partName;
    }

    private IPCore toCore(EDIFCellInst cell) {
        return new IPCore(cell.getCellType().getName(), partName+"_"+requireProperty(cell.getCellType(), "hash"), cell.getName(), getVerilogImpl(cell.getCellType()));
    }

    private String requireProperty(EDIFCell cell, String propName) {
        EDIFPropertyValue prop = cell.getProperty(propName);
        if (prop == null) {
            if (cell.getProperties().isEmpty()) {
                throw new RuntimeException("cell " + cell.getName() + " does not have any properties, while searching for "+propName);
            }
            String propsList = cell.getProperties().entrySet().stream().map(e -> e.getKey() + " -> " + e.getValue()).collect(Collectors.joining());
            throw new RuntimeException("cell " + cell.getName() + "does not have a " + propName + " property. it has: " + propsList);
        }
        return prop.getValue();
    }

    private Path getVerilogImpl(EDIFCell cell) {
        String fn = requireProperty(cell, "filename");
        Path result = edifFile.toAbsolutePath().getParent().resolve(fn);
        if (!Files.exists(result)) {
            throw new RuntimeException("did not find impl at " + result);
        }
        return result;
    }

    @Override
    public Map<String, IPCore> getPartAndIPNames(EDIFNetlist topEdifNetlist) {
        EDIFCell topCell = topEdifNetlist.getTopCell();
        return topCell.getCellInsts().stream()
                .filter(c -> c.getCellType().getLibrary() == topCell.getLibrary()) //Exclude GND, VCC
                .collect(Collectors.toMap(
                        EDIFName::getName,
                        c -> toCore(c)
                ));
    }


    private static void renameLibrary(EDIFLibrary formerLibrary, EDIFLibrary newLib) {

        if (formerLibrary != null && formerLibrary != newLib) {
            for (EDIFCell cell : formerLibrary.getCells()) {
                newLib.addCell(cell);
            }
            formerLibrary.getCells().clear();
            formerLibrary.getNetlist().removeLibrary(formerLibrary.getName());
        }
    }

    private static void renameLibraries(EDIFNetlist netlist) {
        renameLibrary(netlist.getLibrary("LIB"), netlist.getHDIPrimitivesLibrary());
        renameLibrary(netlist.getTopCell().getLibrary(), netlist.getWorkLibrary());
    }

    private void makeUndrivenNetsStatic(EDIFNetlist netlist) {
        EDIFCell topCell = netlist.getTopCell();
        EDIFNet gndNet = EDIFTools.getStaticNet(NetType.GND, topCell, netlist);
        for (EDIFCellInst cellInst : topCell.getCellInsts()) {
            for (EDIFPortInst portInst : cellInst.getPortInsts()) {
                EDIFNet net = portInst.getNet();
                if (portInst.isInput() && net.getPortInsts().size() == 1) {
                    System.out.println("Tying undriven port "+portInst+" low");
                    net.removePortInst(portInst);
                    gndNet.addPortInst(portInst);
                    topCell.removeNet(net);
                }
            }
        }
    }

    private static void moveBlackboxes(EDIFNetlist netlist) {
        for (Iterator<EDIFCell> iter = netlist.getHDIPrimitivesLibrary().getCellMap().values().iterator(); iter.hasNext(); ) {
            EDIFCell item = iter.next();
            if (item.getProperty("blackbox")!=null) {
                System.out.println("moving blackbox element"+item);
                iter.remove();
                netlist.getWorkLibrary().addCell(item);
            }
        }
    }

    @Override
    protected void implementBlocks(Map<String, IPCore> ipNames, ImplGuide implHelper, Device device) {
        BlockCreatorVerilog.synthBlocks(ipNames, cacheDir.toFile().getAbsolutePath(), partName);
        BlockCreator.implementBlocks(ipNames, cacheDir.toFile().getAbsolutePath(), implHelper, device);
    }

    @Override
    protected EDIFNetlist readEDIF() {
        EDIFNetlist edifNetlist = super.readEDIF();
        renameLibraries(edifNetlist);
        moveBlackboxes(edifNetlist);
        makeUndrivenNetsStatic(edifNetlist);

        return edifNetlist;
    }

    private void enterClockPeriods(Design stitched, Map<String, ClockConstraint> clockConstraints) {
        for (SiteInst siteInst : stitched.getSiteInsts()) {
            if (siteInst.getSiteTypeEnum() == SiteTypeEnum.MMCM || siteInst.getSiteTypeEnum() == SiteTypeEnum.MMCME2_ADV || siteInst.getSiteTypeEnum() == SiteTypeEnum.MMCME3_ADV) {
                if (siteInst.getCells().size()!=1) {
                    throw new RuntimeException("not one cell in "+siteInst+", but: "+siteInst.getCells());
                }
                Cell cell = siteInst.getCells().iterator().next();

                for (SitePinInst pin : siteInst.getSitePinInsts()) {
                    if (!pin.getName().startsWith("CLKIN")) {
                        continue;
                    }
                    if (pin.getNet() == null) {
                        continue;
                    }
                    SitePinInst src = pin.getNet().getSource();
                    if (src == null) {
                        continue;
                    }
                    Cell srcCell = src.getSiteInst().getCell("INBUF_EN");
                    if (srcCell == null) {
                        throw new RuntimeException("did not find cell in "+src.getSiteInst());
                    }
                    ClockConstraint constraint = clockConstraints.get(srcCell.getName());
                    if (constraint == null) {
                        throw new RuntimeException("no clock constraint on input pin "+srcCell.getName());
                    }

                    String paramName = "CLKIN"+pin.getName().charAt(pin.getName().length()-1)+"_PERIOD";

                    EDIFPropertyValue existing = cell.getProperty(paramName);
                    if (existing == null) {
                        throw new RuntimeException("did not find property named "+paramName+" on "+cell.getEDIFCellInst());
                    }

                    existing.setValue(constraint.getPeriod());
                    System.out.println("cell.getProperties() = " + cell.getProperties());


                }


            }
        }
    }

    private Map<String, String> loadNameMapping(Path nameMappingFile) {
        try {
            return Files.lines(nameMappingFile)
                    .map(s -> {
                        String[] split = s.split("\t");
                        if (split.length != 2) {
                            throw new RuntimeException("invalid line: "+s+" in "+nameMappingFile);
                        }
                        return split;
                    })
                    .collect(Collectors.toMap(a->a[0].substring(1), a->a[1].substring(1)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String rewriteCellName(Map<String, String> nameMapping, String cellName) {
        System.out.println("rewriting "+cellName);
        String[] split = cellName.split("/");
        for (int elements = split.length; elements > 0; elements--) {
            String partsName = Arrays.stream(split, 0, elements).collect(Collectors.joining("/"));
            System.out.println("trying "+partsName);
            String rewritten = nameMapping.get(partsName);
            if (rewritten != null) {
                System.out.println("found: "+rewritten);
                if (elements == split.length) {
                    System.out.println("rewriting to "+rewritten);
                    return rewritten;
                }
                String rest = Arrays.stream(split, elements, split.length).collect(Collectors.joining("/"));

                String result = rewritten + '/' + rest;

                System.out.println("rewriting to "+result);
                return result;
            }
        }
        System.out.println("no mapping found");
        return cellName;
    }

    private EDIFCellInst findCell(Design design, Map<String, String> nameMapping, String cellName) {
        String rewritten = rewriteCellName(nameMapping, cellName);
        EDIFCellInst cellInst = design.getNetlist().getCellInstFromHierName(rewritten);
        if (cellInst == null) {
            cellInst = design.getNetlist().getCellInstFromHierName(rewritten.replaceAll("[^a-zA-Z0-9/]","_"));
        }
        if (cellInst == null) {
            throw new RuntimeException("cell not found: "+rewritten+ " (rewritten from "+cellName+")");
        }
        return cellInst;
    }

    private void overrideProperties(Design design, Path nameMappingFile, Map<String, Map<String, String>> cellProperties) {
        Map<String, String> nameMapping;
        if (Files.exists(nameMappingFile)) {
            nameMapping = loadNameMapping(nameMappingFile);
        } else {
            nameMapping = new HashMap<>();
        }
        cellProperties.forEach((cellName, properties) -> {
            EDIFCellInst cell = findCell(design, nameMapping, cellName);
            properties.forEach((propName, propVal) -> {
                EDIFPropertyValue edifVal = cell.getProperty(propName);
                if (edifVal == null) {
                    throw new RuntimeException("property "+propName+" not found in "+cell);
                }
                //Strip quotes
                if (propVal.startsWith("\"") && propVal.endsWith("\"")) {
                    propVal = propVal.substring(1, propVal.length()-1);
                }
                edifVal.setValue(propVal);
            });
        });
    }

    @Override
    protected void finalizeDesign(XDCConstraints constraints, Design stitched) {
        enterClockPeriods(stitched, constraints.getClockConstraints());
        overrideProperties(stitched, FileTools.replaceExtension(edifFile, "_name_mapping.csv"), constraints.getCellProperties());
    }

    @Override
    public List<SiteInst> stitchDesign(Design design, Map<String, PackagePinConstraint> constraints, Map<String, ModuleImplsInstance> moduleInsts) {
        addClkBuffer(design);
        return super.stitchDesign(design, constraints, moduleInsts);
    }

    private void addClkBuffer(Design design) {
        design.getTopEDIFCell().getPorts().stream().filter(p->p.getName().contains("clk") && p.isInput())
                .forEach(clkport -> {
                    System.out.println("adding clock buffer for "+clkport);

                    EDIFNetlist n = design.getNetlist();
                    EDIFCell parent = n.getTopCell();

                    // Create BUFGCE in netlist and connect it
                    EDIFCellInst bufgce = Design.createUnisimInst(parent, clkport.getName()+"_buf", Unisim.BUFGCE);
                    EDIFNet clkInNet = parent.getNet(clkport.getName()); //TODO don't just assume the net has the same name

                    EDIFNet clkBufNet = parent.createNet(clkport.getName()+"_buf");


                    for (Iterator<EDIFPortInst> iter = clkInNet.getPortInsts().iterator(); iter.hasNext();) {
                        final EDIFPortInst portInst = iter.next();
                        if (portInst.getCellInst() != null) {
                            iter.remove();
                            clkBufNet.addPortInst(portInst);
                        }
                    }

                    clkInNet.createPortInst("I", bufgce);

                    clkBufNet.createPortInst("O", bufgce);
                    EDIFNet vccNet = EDIFTools.getStaticNet(NetType.VCC, parent, n);
                    vccNet.createPortInst("CE", bufgce);
                    SLRCrosserGenerator.placeBUFGCE(design, design.getDevice().getSite("BUFGCE_X0Y8"), bufgce.getName());
                });


        //netlist.getTopCell().getNet("ap_clk").getPortInsts().removeIf(edifPortInst -> edifPortInst.getCellInst() == null);
        //netlist.getTopCell().getPortMap().remove("ap_clk");
    }

    public static void main(String[] args) throws FileNotFoundException {

        OptionParser optionParser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> edifFileOption = optionParser.accepts("in", "Input EDIF file").withRequiredArg().required();
        ArgumentAcceptingOptionSpec<String> cacheOption = optionParser.accepts("cache", "Cache Directory").withRequiredArg().required();
        ArgumentAcceptingOptionSpec<String> partOption = optionParser.accepts("part", "FPGA Part").withRequiredArg().required();
        OptionSpec<?> outOfContextOption = optionParser.accepts("ooc", "Out of Context");
        OptionSpec<?> writeVisualizationOption = optionParser.accepts("visualization", "Write Placment Visualization");


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

        Path edifFile = Paths.get(options.valueOf(edifFileOption));
        if (!Files.exists(edifFile)) {
            throw new FileNotFoundException("Input Edif does not exist at "+edifFile);
        }
        Path cache = Paths.get(options.valueOf(cacheOption));

        String part = options.valueOf(partOption);

        boolean outOfContext = options.has(outOfContextOption);
        boolean writeVisualization = options.has(writeVisualizationOption);


        new VerilogStitcher(cache, edifFile, part).stitch(outOfContext, writeVisualization);
    }
}
