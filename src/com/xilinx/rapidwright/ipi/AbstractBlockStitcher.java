package com.xilinx.rapidwright.ipi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import com.trolltech.qt.gui.QApplication;
import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.ConstraintGroup;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleImpls;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.NetType;
import com.xilinx.rapidwright.design.PinType;
import com.xilinx.rapidwright.design.Port;
import com.xilinx.rapidwright.design.PortType;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.blocks.BlockGuide;
import com.xilinx.rapidwright.design.blocks.BlockInst;
import com.xilinx.rapidwright.design.blocks.ImplGuide;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.PartNameTools;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFHierCellInst;
import com.xilinx.rapidwright.edif.EDIFHierNet;
import com.xilinx.rapidwright.edif.EDIFHierPortInst;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPortInst;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.gui.ModuleInstanceScene;
import com.xilinx.rapidwright.gui.UiTools;
import com.xilinx.rapidwright.placer.blockplacer.BlockPlacer2;
import com.xilinx.rapidwright.placer.handplacer.FloorPlanScene;
import com.xilinx.rapidwright.placer.handplacer.HandPlacer;
import com.xilinx.rapidwright.router.Router;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;
import com.xilinx.rapidwright.util.Utils;

public abstract class AbstractBlockStitcher {
    public static final boolean DUMP_SYNTH_DCP_ONLY = false;

    public static final boolean CREATE_ROUTED_DCP = false;

    public static final boolean OPEN_HAND_PLACER = false;
    public static final String CACHE_ID = "CACHE_ID";

    public static final String BLOCK_NAME = "BLOCK_NAME";
    public static final boolean INSTANCE_PORT_IOs = true;
    public static final boolean REPORT_UNCONNECTED = false;
    HashMap<String,EDIFCellInst> instNameToInst = new HashMap<String,EDIFCellInst>();

    private HashSet<String> reportedUnconnects = new HashSet<>();

    protected String partName;

    protected final Path cacheDir;
    protected final Path edifFile;
    private HashMap<String,ArrayList<EDIFCellInst>> ipiBlockInstanceMap = null;

    protected AbstractBlockStitcher(Path cacheDir, Path edifFile) {
        this.cacheDir = cacheDir;
        this.edifFile = edifFile;
    }


    private ArrayList<EDIFHierPortInst> getPassThruPortInsts(Port port, EDIFHierPortInst curr){
        ArrayList<EDIFHierPortInst> list = new ArrayList<>();
        for(String name : port.getPassThruPortNames()){
            EDIFPortInst passThruInst = curr.getPortInst().getCellInst().getPortInst(name);
            if(passThruInst == null) {
                String unconnected = curr.getPortInst().getCellInst().getName() + EDIFTools.EDIF_HIER_SEP + name;
                if(!reportedUnconnects.contains(unconnected)){
                    System.out.println("INFO: Port " + unconnected + " is unconnected");
                    reportedUnconnects.add(unconnected);
                }
                continue;
            }
            EDIFHierPortInst passThru = new EDIFHierPortInst(curr.getHierarchicalInst(), passThruInst);
            list.add(passThru);
        }
        return list;
    }

    private Net validateNet(Net curr, Net found){
        if(curr == null) return found;
        if(!curr.equals(found)){
            throw new RuntimeException("ERROR: Consolidation of nets failed: curr=" + curr + " found=" + found);
        }
        return found;
    }

    /**
     * Stitches the logical netlist components into the black boxes of the top-level EDIF netlist.
     * Also stitches together the physical nets.
     * @param design
     * @param constraints
     * @return List of SiteInsts that were removed from their module
     */
    public List<SiteInst> stitchDesign(Design design, Map<String,PackagePinConstraint> constraints){
        boolean debug = false;
        EDIFNetlist n = design.getNetlist();
        // Create a reverse parent net map (Parent Net -> Children Nets: all logical nets that are physically equivalent)
        HashMap<EDIFHierNet,ArrayList<EDIFHierNet>> reverseMap = new HashMap<>();
        for(Map.Entry<EDIFHierNet,EDIFHierNet> e : n.getParentNetMap().entrySet()){
            ArrayList<EDIFHierNet> l = reverseMap.computeIfAbsent(e.getValue(), k -> new ArrayList<>());
            l.add(e.getKey());
        }

        HashSet<String> addedPorts = new HashSet<>();
        HashMap<String,ArrayList<EDIFHierPortInst>> portGroups = new HashMap<>();
        // For each parent (physical) net...
        for(Map.Entry<EDIFHierNet,ArrayList<EDIFHierNet>> e : reverseMap.entrySet()){
            if(debug) System.out.println(e.getKey() + ":");
            ArrayList<EDIFHierPortInst> absPortInsts = new ArrayList<>();
            EDIFHierNet absNet = e.getKey();
            if(absNet == null){
                throw new RuntimeException("ERROR: Couldn't find net named " + e.getKey());
            }
            // For each child (physically equivalent) net...
            for(EDIFHierNet netName : e.getValue()){
                absNet = netName;
                // Get all the port instances that belong to these nets
                for(EDIFPortInst p : absNet.getNet().getPortInsts()){
                    EDIFHierPortInst absPort = new EDIFHierPortInst(absNet.getHierarchicalInst(), p);
                    if(addedPorts.contains(absPort.toString())) continue;
                    addedPorts.add(absPort.toString());
                    absPortInsts.add(absPort);
                }
            }
            // For each port instance connected to the group of physically equivalent nets...
            for(EDIFHierPortInst p : absPortInsts){
                // Create a port group - physically equivalent set of ports
                portGroups.put(p.toString(), absPortInsts);
                if(debug) System.out.println("  "+ p.getFullHierarchicalInstName() + "/"+ p.getPortInst().getName());
                ModuleInst mi = design.getModuleInst(p.getFullHierarchicalInstName());
                if(mi == null) continue;
                Port port = mi.getPort(p.getPortInst().getName());
                if(debug) System.out.println("    (PORT) " + port.getType() + " " + port.getName() + " " + port.getPassThruPortNames());
            }
        }

        HashSet<String> visited = new HashSet<>();
        HashMap<EDIFHierPortInst,Net> topPortsMap = new HashMap<>();
        // Connect port groups through pass-thru connections from Module Port meta-data
        for(Map.Entry<String, ArrayList<EDIFHierPortInst>> e : portGroups.entrySet()){
            if(visited.contains(e.getKey())) continue;
            Queue<EDIFHierPortInst> q = new LinkedList<>(e.getValue());
            ArrayList<Net> nets = new ArrayList<>();
            HashSet<String> netsAlreadyVisited = new HashSet<>();
            ArrayList<EDIFHierPortInst> topPorts = new ArrayList<>();
            Net newNet = null;
            while(!q.isEmpty()){
                EDIFHierPortInst curr = q.poll();
                if(visited.contains(curr.toString())) continue;
                visited.add(curr.toString());
                ModuleInst mi = design.getModuleInst(curr.getFullHierarchicalInstName());
                if(mi == null) {
                    EDIFCellInst inst = curr.getPortInst().getCellInst();
                    if(inst != null){
                        // Internal VCC/GND source
                        if(inst.getCellType().getName().equals("GND")) {
                            newNet = validateNet(newNet, design.getGndNet());
                        }
                        else if(inst.getCellType().getName().equals("VCC")) {
                            newNet = validateNet(newNet, design.getVccNet());
                        }
                    } else if(curr.getHierarchicalInstName().isEmpty()){
                        topPorts.add(curr);
                    }
                    continue;
                }
                Port port = mi.getPort(curr.getPortInst().getName());
                if (port == null) {
                    throw new NullPointerException("port "+curr+" is null in module "+mi.getName()+", a "+mi.getModule().getName());
                }
                if(REPORT_UNCONNECTED && !port.isOutPort() && port.getType() == PortType.UNCONNECTED){
                    MessageGenerator.briefError("WARNING: " + curr + " is unconnected internally.");
                }
                for(EDIFHierPortInst passThru : getPassThruPortInsts(port, curr)){
                    String passThruName = passThru.toString();
                    if(visited.contains(passThruName)) continue;
                    ArrayList<EDIFHierPortInst> passThruGroup = portGroups.get(passThruName);
                    if(passThruGroup == null){
                        // This is likely a pass-thru that is static-driven and down stream
                        // sinks have not been explored
                        EDIFHierPortInst portInst = n.getHierPortInstFromName(passThruName);
                        passThruGroup = new ArrayList<>();
                        for(EDIFPortInst pi : portInst.getPortInst().getNet().getPortInsts()){
                            passThruGroup.add(new EDIFHierPortInst(portInst.getHierarchicalInst(), pi));
                        }

                    }
                    q.addAll(passThruGroup);
                }
                Net net = getCorrespondingNet(curr,design);
                if(net == null) {
                    if (curr.getPortInst().getCellInst() == null) {
                        //Found an unconnected toplevel port
                        topPortsMap.put(curr, null);
                    }
                    continue;
                }
                if(netsAlreadyVisited.contains(net.getName())) continue;
                netsAlreadyVisited.add(net.getName());

                if(net.getType() == NetType.GND) {
                    newNet = validateNet(newNet, design.getGndNet());
                }
                else if(net.getType() == NetType.VCC){
                    newNet = validateNet(newNet, design.getVccNet());
                }else{
                    nets.add(net);
                    if(net.getSource() != null){
                        newNet = validateNet(newNet, net);
                    }
                }
            }

            if(newNet == null){
                if(nets.size() != 0) {
                    // This is not a new with a top-level input yet to be instantiated
                    newNet = nets.get(0);
                }
            }

            for(Net net : nets){
                if(net.equals(newNet)) continue;
                design.movePinsToNewNetDeleteOldNet(net, newNet, true);
            }
            for(EDIFHierPortInst p : topPorts){
                topPortsMap.put(p, newNet);
            }
        }

        // Handle top level pins / IO instantiation
        // Note that it appears like some top-level pins might already have IOs instantiated (clk_wiz)
        if(INSTANCE_PORT_IOs && constraints != null && constraints.size() > 0){

            nextPort: for(Map.Entry<EDIFHierPortInst, Net> e : topPortsMap.entrySet()){
                String portName = e.getKey().getPortInst().getName();
                Net portNet = null;
                portNet = e.getValue();

				Site site = design.getDevice().getSiteFromPackagePin(constraints.get(portName).getName());
                if(site == null){
                    MessageGenerator.briefMessage("WARNING: It appears that the I/O called " + portName + " is not assigned to a package pin!");
                    continue nextPort;
                }

                // Check for IOs that already exist, we can skip instantiation
                boolean isPortOutput = e.getKey().isOutput();
                if (portNet != null) {
                    for (SitePinInst p : portNet.getPins()) {
                        boolean portDirMatch = isPortOutput == !p.isOutPin();
                        if (portDirMatch && p.getSite().getName().startsWith("IOB_") && p.getSite() == site) {
                            MessageGenerator.briefMessage("INFO: IOB already instantiated for " + e.getKey());
                            continue nextPort;
                        }
                    }
                }

                SiteInst inst = design.getSiteInstFromSite(site);
                if(inst != null){
                    // IO Site has already been created
                    continue;
                }

                String ioStandard = constraints.get(portName).getIoStandard();
                String pkgPin = constraints.get(portName).getName();
                EDIFNet logNet = e.getKey().getPortInst().getNet();


                logNet.removePortInst(e.getKey().getPortInst());

                EDIFNet logNetRenamed = new EDIFNet(logNet.getName()+"_buf", logNet.getParentCell());

                List<EDIFPortInst> toMove = new ArrayList<>(logNet.getPortInsts());
                for (EDIFPortInst p : toMove) {
                    logNet.removePortInst(p);
                    logNetRenamed.addPortInst(p);
                }

                //Net portNetRename = new Net(portNet.getName()+"_buf", logNetRenamed);
                //design.addNet(portNetRename);gg
                //design.movePinsToNewNetDeleteOldNet(portNet, portNetRename, true);
                //portNet = portNetRename;

                //design.removeNet(portNet);


                Cell iob = design.createAndPlaceIOB(portName, isPortOutput ? PinType.OUT : PinType.IN, pkgPin, ioStandard);

                String iobPortName;
                if (isPortOutput) {
                    iobPortName = "I";
                } else {
                    iobPortName = "O";
                }

                SitePinInst spi;
                if (portNet == null) {
                    portNet = new Net(logNet.getName());
                    design.addNet(portNet);
                }
                portNet.setLogicalNet(logNetRenamed);
                spi = DesignTools.createPinAndAddToNet(iob, iobPortName, portNet);
                spi.setNet(portNet);
/*
                EDIFPortInst iobToDesign = new EDIFPortInst(
                        iob.getEDIFCellInst().getCellType().getPort(iobPortName),
                        logNetRenamed,
                        e.getKey().getPortInst().getIndex(),
                        iob.getEDIFCellInst()
                );

                var belPin = iob.getBELPin(iobToDesign);
                SitePinInst physicalIobToDesign = iob.getSitePinFromPortInst(iobToDesign, null);
                if (physicalIobToDesign == null) {
                    throw new RuntimeException("physical Pin not found");
                }
                portNet.addPin(physicalIobToDesign);*/


            }
        }

        HashMap<Site, SiteInst> uniqueMap = new HashMap<Site, SiteInst>();
        List<SiteInst> nonModuleInsts = new ArrayList<>();
        for(SiteInst i : design.getSiteInsts()){
            if(!Utils.isModuleSiteType(i.getSiteTypeEnum())){
                i.detachFromModule();
                for(SitePinInst p : i.getSitePinInsts()){
                    if (p.getNet()==null) {
                        throw new NullPointerException("no net for "+p+", "+i.getName()+" / "+i.getSiteTypeEnum()+" /" +i.getCells());
                    }
                    p.getNet().unroute();
                }
                Site site = i.getSite();
                if(site != null){
                    if(uniqueMap.containsKey(i.getSite())){
                        SiteInst duplicate = uniqueMap.get(site);
                        System.out.println("WARNING: Found duplicate site used by instances: " + i.getName() + " " + duplicate.getName() + " " + i.getSiteName());
                    }else{
                        uniqueMap.put(i.getSite(),i);
                    }
                }else{
                    if(i.getModuleInst() == null){
                        System.out.println("WARNING: Unplaced site outside of module instance: " + i.getName()  + " "+ i.getSiteTypeEnum());
                    }
                }
                nonModuleInsts.add(i);
            }
        }
        return nonModuleInsts;
    }


    private void writePlacementVisualization(Design design, Path visuDir) {
        System.out.println("Generating Visualization");
        //QApplication.setGraphicsSystem("raster");
        QApplication.initialize(new String[]{});
        try {
            Files.createDirectories(visuDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FloorPlanScene fps = new FloorPlanScene(design, false);
        //fps.setUseImage(false);
        fps.openHMDesign();
        UiTools.saveAsPdf(fps, visuDir.resolve("floorplan.pdf").toFile());

        for (ModuleInst mi : design.getModuleInsts()) {
            ModuleInstanceScene scene = new ModuleInstanceScene(mi);
            scene.setUseImage(false);
            UiTools.saveAsPdf(scene, visuDir.resolve(mi.getName()+".pdf").toFile());
        }

    }


    public Net getCorrespondingNet(EDIFHierPortInst pr, Design design){
        String modInstName = pr.getFullHierarchicalInstName();
        ModuleInst mi = design.getModuleInst(modInstName);
        if(mi == null){
            //throw new RuntimeException("ERROR: No module instance named: " +modInstName);
            return null;
        }
        Port port = mi.getModule().getPort(pr.getPortInst().getName());
        if(port.getType() == PortType.UNCONNECTED)
            return null;
        if(port.getSitePinInsts().isEmpty()){
            if(port.getType() == PortType.GROUND) return design.getGndNet();
            if(port.getType() == PortType.POWER) return design.getVccNet();
            return null;
        }
        if(port.getNet() == null){
            return null;
        }
        String netName = modInstName + "/" + port.getNet().getName();
        Net net = design.getNet(netName);

        return net;
    }


    public boolean netlistHasBlock(Design stitched, String blockName){
        String netlistPrefix = stitched.getNetlist().getName() + "_";
        String blockName2 = blockName.replaceFirst(netlistPrefix, "");
        String blockName3 = blockName2.substring(0, blockName2.length()-2);

        if(ipiBlockInstanceMap == null){
            ipiBlockInstanceMap = new HashMap<String,ArrayList<EDIFCellInst>>();
            Queue<EDIFCell> q = new LinkedList<EDIFCell>();
            q.add(stitched.getNetlist().getTopCell());
            while(!q.isEmpty()){
                EDIFCell curr = q.poll();
                for(EDIFCellInst eci : curr.getCellInsts()){
                    if(eci.getCellType().getName().equals("GND") || eci.getCellType().getName().equals("VCC")){
                        continue;
                    }
                    ArrayList<EDIFCellInst> insts = ipiBlockInstanceMap.get(eci.getName());
                    if(insts == null){
                        insts = new ArrayList<EDIFCellInst>();
                        ipiBlockInstanceMap.put(eci.getName(), insts);
                    }
                    insts.add(eci);
                    //EDIFCellInst eciErr = ipiBlockInstanceMap.put(eci.getName(),eci);
                    if(eci.getCellType().getCellInsts() != null){
                        q.add(eci.getCellType());
                    }
                }
            }
        }

        return ipiBlockInstanceMap.containsKey(blockName) || ipiBlockInstanceMap.containsKey(blockName2) || ipiBlockInstanceMap.containsKey(blockName3);
    }

    public abstract Map<String,IPCore> getPartAndIPNames(EDIFNetlist topEdifNetlist);

    public void populateModuleInstMaps(EDIFNetlist netlist){
        Queue<EDIFHierCellInst> queue = new LinkedList<EDIFHierCellInst>();
        netlist.getTopHierCellInst().addChildren(queue);
        while(!queue.isEmpty()){
            EDIFHierCellInst p = queue.poll();
            EDIFCellInst i = p.getInst();
            if(i.getName().equals("VCC") || i.getName().equals("GND")) continue;
            instNameToInst.put(p.getFullHierarchicalInstName(), i);
            p.addChildren(queue);
        }
    }

    public void stitch() {
        stitch(true, false);
    }

    public void stitch(boolean outOfContext, boolean writeVisualization) {

        File cache = cacheDir.toFile();
        CodePerfTracker t = new CodePerfTracker("BlockStitcher", false);
        t.start("Init");
        long[] runtimes = new long[6];
        runtimes[0] = runtimes[1] = System.currentTimeMillis();

        if (!cache.exists()) {
            throw new RuntimeException("ERROR: BlockGuide cache directory does not exist!");
        }


        ImplGuide implHelper = null;
        HashSet<String> unusedImplGuides = null;

        boolean buildExampleGuideFile = false;
        if(!DUMP_SYNTH_DCP_ONLY){
            Path implGuideFileName = FileTools.replaceExtension(edifFile, ".igf");
            if(Files.exists(implGuideFileName)){
                implHelper = ImplGuide.readImplGuide(implGuideFileName.toString());
                unusedImplGuides = new HashSet<>(implHelper.getBlockNames());
            }else{
                System.out.println("INFO: No .igf file found, proceeding with auto block placement and routing.");
                buildExampleGuideFile = true;
            }
        }
        t.stop().start("Reading Top Level EDIF");
        EDIFNetlist topEdifNetlist = readEDIF();

        Map<String,IPCore> ipNames = getPartAndIPNames(topEdifNetlist);

        Design stitched = new Design("top_stitched", partName);
        stitched.setNetlist(topEdifNetlist);
        t.stop().start("Implement Blocks");

        populateModuleInstMaps(topEdifNetlist);
        if(!FileTools.isVivadoOnPath()){
            MessageGenerator.briefError("WARNING: Vivado executable is not on path.  All BlockStitcher implementation builds will fail.");
        }

        implementBlocks(ipNames, implHelper, stitched.getDevice());

        HashMap<String,String> modInstName2CacheID = new HashMap<String, String>();
        t.stop().start("Retrieve Blocks from Cache");
        int totalBlocks = 0;
        List<String> fail = new ArrayList<>();
        HashMap<ModuleInst,EDIFNetlist> miMap = new HashMap<ModuleInst,EDIFNetlist>();
        for(Map.Entry<String,IPCore> e : ipNames.entrySet()){
            String blockName = e.getKey();
            String cacheID = e.getValue().getHash();
            String longName =  "block "+blockName+", cache id "+cacheID;
            System.out.println("loading "+longName);
            try {
                if (implHelper != null) {
                    unusedImplGuides.remove(cacheID);
                }

                File dir2 = new File(cacheDir + File.separator + cacheID);
                String routedDCPFileName = null;
                String edifFileName = null;
                String xciFileName = null;
                int blockImplCount = 0;
                if (dir2.list() == null || dir2.list().length == 0) {
                    throw new RuntimeException("ERROR: Cached entry " + dir2 + " for ip " + blockName + " is empty!");
                }
                for (String d2 : dir2.list()) {
                    File f = new File(d2);
                    if (f.getName().endsWith(BlockCreator.ROUTED_DCP_SUFFIX) && !f.getName().contains("roundtrip")) {
                        routedDCPFileName = cacheDir + File.separator + cacheID + File.separator + f.getName();
                        blockImplCount++;
                    } else if (f.getName().endsWith(BlockCreator.ROUTED_EDIF_SUFFIX)) {
                        edifFileName = cacheDir + File.separator + cacheID + File.separator + f.getName();
                    }
                }

                if (!netlistHasBlock(stitched, blockName)) {
                    System.out.println("WARNING: Skipped block " + blockName);
                    continue;
                }

                xciFileName = dir2 + "/" + cacheID + ".xci";
                //System.out.println(routedDCPFileName + " " + edifFileName + " " + xciFileName);

                if (edifFileName == null) {
                    System.err.println("no edif file found for cache id " + cacheID + " for module " + blockName);
                }

                ModuleImpls modImpls = BlockCreator.createOrRetrieveBlock(edifFileName, routedDCPFileName, blockName, xciFileName, blockImplCount);
                for (Module m : modImpls) {
                    // Add Cache ID to Module
                    m.getMetaDataMap().put(CACHE_ID, cacheID);
                    m.getMetaDataMap().put(BLOCK_NAME, blockName);
                }
                totalBlocks++;

                String modInstName = e.getValue().getInstName();
                modInstName2CacheID.put(modInstName, cacheID);

                int implementationIndex = 0;

                if (implHelper != null) {
                    BlockGuide blockGuide = implHelper.getBlock(cacheID);
                    if (blockGuide != null) {
                        BlockInst bi = blockGuide.getInst(modInstName);
                        if (bi == null) {
                            throw new RuntimeException("ERROR: Missing placement for " + modInstName + " in .igf file.");
                        }
                        implementationIndex = bi.getImplIndex();
                    }
                }
                //System.out.println(modInstName + " " + implementationIndex);
                EDIFNetlist tmp = stitched.getNetlist();
                stitched.setNetlist(null);
                Module module = Objects.requireNonNull(modImpls.get(implementationIndex));
                ModuleInst mi = stitched.createModuleInst(modInstName, module);
                stitched.setNetlist(tmp);
                miMap.put(mi, modImpls.getNetlist());
                SiteInst anchor = mi.getModule().getAnchor();
                if (anchor != null) {
                    mi.place(anchor.getSite());
                    //System.out.println("Placed: " + mi.place(anchor.getSite(), stitched.getDevice()) + " " + totalBlocks);
                }
            } catch (RuntimeException ex) {
                //throw new RuntimeException("failed during "+longName, ex);
                System.err.println("failed during "+longName);
                ex.printStackTrace();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ex.printStackTrace(new PrintStream(bos));
                fail.add(bos.toString());
            }

        }
        if (!fail.isEmpty()) {
            throw new RuntimeException(String.join("\n", fail));
        }

        // Check for unused impl guide directives
        if(implHelper != null){
            for(String id : unusedImplGuides){
                System.out.println("WARNING: Unused impl guide ID " + id);
            }
        }


        // Update package in case block loading changed it
        stitched.getDevice().setActivePackage(PartNameTools.getPart(partName).getPkg());
        stitched.getNetlist().setDevice(stitched.getDevice());

        runtimes[1] = System.currentTimeMillis() - runtimes[1];
        runtimes[2] = System.currentTimeMillis();
        System.out.println("Total Blocks : " + totalBlocks);

        String xdcFileName = FileTools.replaceExtension(edifFile, ".xdc").toString();
        XDCConstraints constraints = readConstraintsOrCreateArbitrary(topEdifNetlist, stitched, xdcFileName, outOfContext);
        t.stop().start("Stitch Design");

        List<SiteInst> nonModuleSiteInsts = stitchDesign(stitched, constraints.getPinConstraints());

        Set<String> uniqifiedNetlists = new HashSet<>();
        for(Map.Entry<ModuleInst,EDIFNetlist> e : miMap.entrySet()){
            //System.out.println(" MAPPINGS: " + e.getKey() + " " + e.getValue() + " " + stitcher.instNameToInst.get(e.getKey().getName()) );
            if(uniqifiedNetlists.contains(e.getValue().getName())) continue;
            uniqifiedNetlists.add(e.getValue().getName());
            stitched.repopulateNetlistOfModuleInst(e.getKey(), e.getValue());
        }

        EDIFCell top = stitched.getNetlist().getTopCell();
        EDIFLibrary work = stitched.getNetlist().getLibrary(EDIFTools.EDIF_LIBRARY_WORK_NAME);
        work.addCell(top);
        for(Map.Entry<ModuleInst,EDIFNetlist> e : miMap.entrySet()){
            EDIFCellInst inst = EDIFTools.getEDIFCellInst(stitched.getNetlist(), e.getKey().getName());//top.getCellInstance(e.getKey().getName());
            if(inst == null) throw new RuntimeException("ERROR: Couldn't update EDIF cell instance.");
            EDIFCell cellType = work.getCell(e.getValue().getName() + "_" + e.getValue().getName());
            if(cellType == null) throw new RuntimeException("ERROR: Couldn't update EDIF cell type " + e.getValue().getName());
            inst.updateCellType(cellType);
        }

        EDIFLibrary ipLib = stitched.getNetlist().getLibrary("IP_Integrator_Lib");
        if (ipLib != null) {
            for (EDIFCell c : ipLib.getCells()) {
                work.addCell(c);
            }
        }

        ArrayList<String> libsToRemove = new ArrayList<>();
        for(EDIFLibrary lib : stitched.getNetlist().getLibraries()){
            if(lib.getName().equals(EDIFTools.EDIF_LIBRARY_HDI_PRIMITIVES_NAME) ||
                    lib.getName().equals(EDIFTools.EDIF_LIBRARY_WORK_NAME)) continue;
            libsToRemove.add(lib.getName());
        }
        for(String lib : libsToRemove){
            stitched.getNetlist().removeLibrary(lib);
        }
        PlacementLegalizer.legalizeNonModuleSitePlacements(stitched, nonModuleSiteInsts);

        t.stop();
        if(DUMP_SYNTH_DCP_ONLY){
            stitched.unplaceDesign();
            Path dcpName = FileTools.replaceExtension(edifFile, "_rw_synth.dcp");
            stitched.writeCheckpoint(dcpName.toString(),t);
            //EDIFTools.writeEDIFFile(args[1].replace(".edf", "_stitched.edf"), stitched.getNetlist(), stitched.getPartName());
            System.out.println("Wrote Synthesized DCP: " + dcpName);
            return;
        }

        //XPNWriter.writeXPN(stitched, args[1].replace(".edf", ".xpn"), true);
        //XPNWriter.writeXPN(stitched, args[1].replace(".edf", "_not_flat.xpn"), false);
        //EDIFTools.writeEDIFFile(args[1].replace(".edf", "_stitched.edf"), stitched.getNetlist(), stitched.getPartName());

        runtimes[2] = System.currentTimeMillis() - runtimes[2];
        runtimes[3] = System.currentTimeMillis();

        if(new File(xdcFileName).exists()){
            for(String line : FileTools.getLinesFromTextFile(xdcFileName)){
                stitched.addXDCConstraint(ConstraintGroup.LATE,line);
            }
        }

        if(implHelper != null){
            stitched.setAutoIOBuffers(false);
            stitched.setDesignOutOfContext(true);

            int sliceY = 0;  // TODO - Empty blocks are placed in a column so they don't overlap
            t = new CodePerfTracker("CUSTOM PLACER", true);
            t.start("Custom Placement");

            for(ModuleInst mi : stitched.getModuleInsts()) {
                BlockGuide blockHelper = implHelper.getBlock(modInstName2CacheID.get(mi.getName()));
                if (blockHelper == null) {
                    String newAnchor = "SLICE_X167Y" + sliceY++;
                    mi.place(stitched.getDevice().getSite(newAnchor));
                    continue;
                }
                Site s = blockHelper.getInst(mi.getName()).getPlacement();
                PBlock pb = blockHelper.getImplementations().get(blockHelper.getInst(mi.getName()).getImplIndex());
                boolean success = mi.placeMINearTile(s.getTile(), s.getSiteTypeEnum());
                if (!success) {
                    MessageGenerator.briefError("ERROR: Couldn't place " + mi.getName() + " on site " + s.getName());
                }
            }
            t.stop().start("Write Stitched EDIF");
            EDIFTools.writeEDIFFile(FileTools.replaceExtension(edifFile, "_stitched.edf").toString(), stitched.getNetlist(), stitched.getPartName());
            t.stop().start("Save Checkpoint");
            stitched.writeCheckpoint(FileTools.replaceExtension(edifFile,"_placed.dcp").toString());
            t.stop();
            t.printSummary();
            if(OPEN_HAND_PLACER) HandPlacer.openDesign(stitched);
            return;
        }else{
            BlockPlacer2 placer = new BlockPlacer2();
            placer.placeDesign(stitched, false);
        }

        // Create an example impl guide file
        if(buildExampleGuideFile){
            ImplGuide ig = new ImplGuide();
            ig.setPart(stitched.getPart());
            ig.setDevice(stitched.getDevice());

            for(ModuleImpls m : stitched.getModules()){
                BlockGuide bg = ig.createBlockGuide(m.get(0).getMetaDataMap().get(CACHE_ID));
                for(Module m2 : m){
                    bg.addImplementation(m2.getImplementationIndex(), m2.getPBlock());
                }
            }

            for(ModuleInst mi : miMap.keySet()){
                if(mi.getModule().getPBlock() == null) continue;
                if(mi.getModule().getAnchor() == null) continue;
                String cacheID = mi.getModule().getMetaDataMap().get(CACHE_ID);
                BlockGuide bg = ig.getBlock(cacheID);

                BlockInst bi = new BlockInst();
                bi.setImpl(mi.getModule().getImplementationIndex());
                bi.setName(mi.getName());
                bi.setParent(bg);
                try {
                    bi.setPlacement(mi.getLowerLeftPlacement());
                    bg.addBlockInst(bi);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    System.err.println("skipping module " + mi.getName() + " in example guide file");
                }
            }

            // Remove any blocks that don't have pblock/implementations
            ig.removeBlocksWithoutPBlocks();

            ig.writeImplGuide(FileTools.replaceExtension(edifFile, ".igf.example").toString());
        }

        // Need to remove duplicate sites because IPI will generate the same IOs in multiple IP blocks :-(
        HashMap<String, SiteInst> duplicateCheck = new HashMap<>();
        ArrayList<SiteInst> removeThese = new ArrayList<SiteInst>();
        for(SiteInst inst : stitched.getSiteInsts()){
            SiteInst match = duplicateCheck.get(inst.getSiteName());
            if(match != null){
                //System.out.println("Found match: " + match.toString() + " " + inst.toString());
                if(match.getNetList().size() == 0){
                    removeThese.add(match);
                }else{
                    removeThese.add(inst);
                    duplicateCheck.put(inst.getSiteName(), match);
                }

            }else{
                duplicateCheck.put(inst.getSiteName(), inst);
            }
        }
        for(SiteInst i : removeThese){
            stitched.removeSiteInst(i);
        }

        if(OPEN_HAND_PLACER) HandPlacer.openDesign(stitched);



        runtimes[3] = System.currentTimeMillis() - runtimes[3];
        runtimes[4] = System.currentTimeMillis();

        if (writeVisualization) {
            writePlacementVisualization(stitched, FileTools.replaceExtension(edifFile, "_visu"));
        }
        finalizeDesign(constraints, stitched);

        Path placedDCPName = FileTools.replaceExtension(edifFile, "_placed.dcp");
        stitched.writeCheckpoint(placedDCPName.toString());

        Path routedDCPName = FileTools.replaceExtension(edifFile, "_routed.dcp");
        if(CREATE_ROUTED_DCP){
            Router router = new Router(stitched);
            router.routeDesign();

            runtimes[4] = System.currentTimeMillis() - runtimes[4];
            runtimes[5] = System.currentTimeMillis();


            stitched.writeCheckpoint(routedDCPName.toString());
        }else{
            runtimes[4] = System.currentTimeMillis() - runtimes[4];
        }

        //EDIFTools.writeEDIFFile(args[1].replace(".edf", BlockCreator.ROUTED_EDIF_SUFFIX), stitched.getNetlist(), stitched.getPartName());

        runtimes[5] = System.currentTimeMillis() - runtimes[5];
        runtimes[0] = System.currentTimeMillis() - runtimes[0];

        System.out.println();
        System.out.println("----------------- SUMMARY --------------------");
        System.out.printf("           Module Loading Time : %8.3fs \n", runtimes[1]/1000.0);
        System.out.printf("                Stitching Time : %8.3fs \n", runtimes[2]/1000.0);
        System.out.printf("                   Placer Time : %8.3fs \n", runtimes[3]/1000.0);
        if(CREATE_ROUTED_DCP) System.out.printf("                   Router Time : %8.3fs \n", runtimes[4]/1000.0);
        System.out.printf("            Saving Design Time : %8.3fs \n", runtimes[CREATE_ROUTED_DCP ? 5 : 4]/1000.0);
        System.out.println("----------------------------------------------");
        System.out.printf("                 Total Runtime : %8.3fs \n", runtimes[0]/1000.0);

        if(CREATE_ROUTED_DCP) System.out.println("Created Routed DCP at: " + routedDCPName);
        else System.out.println("Created Placed DCP at: " + placedDCPName);
    }

    protected void finalizeDesign(XDCConstraints constraints, Design stitched) {

    }



    private XDCConstraints readConstraintsOrCreateArbitrary(EDIFNetlist topEdifNetlist, Design stitched, String xdcFileName, boolean outOfContext) {
        if(new File(xdcFileName).exists()){
            XDCConstraints constraints = XDCParser.parseXDCNew(xdcFileName, stitched.getDevice());
            if (constraints.getPinConstraints().size() == 0 && !outOfContext) {
                XDCConstraints arbitraryConstraints = ArbitraryConstraintCreator.createArbitraryConstraints(topEdifNetlist.getTopCell(), stitched.getPart(), stitched.getDevice());
                constraints.getPinConstraints().putAll(arbitraryConstraints.getPinConstraints());
            }
            return constraints;
        }
        MessageGenerator.briefError("WARNING: Could not find XDC file " + xdcFileName);
        if (outOfContext) {
            return new XDCConstraints();
        }
        return ArbitraryConstraintCreator.createArbitraryConstraints(topEdifNetlist.getTopCell(), stitched.getPart(), stitched.getDevice());
    }

    protected abstract void implementBlocks(Map<String, IPCore> ipNames, ImplGuide implHelper,  Device device);

    protected EDIFNetlist readEDIF() {
        return EDIFTools.readEdifFile(edifFile);
    }
}
