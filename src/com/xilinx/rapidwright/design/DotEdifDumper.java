package com.xilinx.rapidwright.design;

import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPort;
import com.xilinx.rapidwright.edif.EDIFPortInst;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public class DotEdifDumper extends DotGraphDumper<EDIFCellInst, EDIFPortInst, EDIFPort, EDIFNet, EDIFNetlist>{
    public DotEdifDumper() {
        super(true);
    }

    @Override
    protected Stream<EDIFCellInst> getInstances(EDIFNetlist design) {
        return design.getTopCell().getCellInsts().stream();
    }

    @Override
    protected Stream<EDIFPortInst> getPorts(EDIFCellInst edifCellInst, EDIFNetlist design) {
        return edifCellInst.getPortInsts().stream();
    }

    @Override
    protected Stream<EDIFPort> getPortTemplates(EDIFCellInst edifCellInst) {
        return edifCellInst.getCellType().getPorts().stream();
    }

    @Override
    protected Stream<EDIFNet> getNets(EDIFNetlist design) {
        return design.getTopCell().getNets().stream();
    }

    @Override
    protected Stream<EDIFPortInst> getNetPorts(EDIFNet edifNet) {
        return edifNet.getPortInsts().stream();
    }

    @Override
    protected boolean isOutputPort(EDIFPortInst edifPortInst) {
        return edifPortInst.isOutput() ^ (edifPortInst.getCellInst() == null);
    }

    @Override
    protected boolean isOutputPortTemplate(EDIFPort port) {
        return port.isOutput();
    }

    @Override
    protected String getInstanceName(EDIFCellInst edifCellInst) {
        return edifCellInst.getName()+" ("+edifCellInst.getCellType()+")";
    }

    @Override
    protected String getPortName(EDIFPortInst edifPortInst) {
        return edifPortInst.getName();
    }

    @Override
    protected String getPortTemplateName(EDIFPort port) {
        return port.getBusName();
    }

    @Override
    protected Stream<EDIFPortInst> getRootPorts(EDIFNetlist design) {
        return design.getTopCell().getNets().stream().flatMap(n->n.getPortInsts().stream())
                .filter(p->p.getCellInst() == null).distinct();
    }

    @Override
    protected String getNetName(EDIFNet edifNet) {
        return edifNet.getName();
    }

    @Override
    protected Map<?, ?> getInstanceProperties(EDIFCellInst edifCellInst, EDIFNetlist design) {
        return edifCellInst.getProperties();
    }

    public static void dump(Path to, EDIFNetlist design) {
        try(PrintWriter pw = new PrintWriter(Files.newBufferedWriter(to))) {
            new DotEdifDumper().doDump(design, pw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void dump(Path to, Design design) {
        dump(to, design.getNetlist());
    }

    @Override
    protected String getDebug(EDIFNet edifNet) {
        return "";
    }
}
