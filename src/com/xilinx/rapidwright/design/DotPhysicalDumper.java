package com.xilinx.rapidwright.design;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public class DotPhysicalDumper extends DotGraphDumper<Cell, SitePinInst, Void, Net, Design> {

    public DotPhysicalDumper() {
        super(true);
    }

    @Override
    protected Stream<Cell> getInstances(Design design) {
        return design.getCells().stream();
    }

    @Override
    protected Stream<SitePinInst> getPorts(Cell cell, Design design) {
        return cell.getSiteInst().getSitePinInsts().stream();
    }

    @Override
    protected Stream<Void> getPortTemplates(Cell cell) {
        return Stream.empty();
    }

    @Override
    protected Stream<Net> getNets(Design design) {
        return design.getNets().stream().filter(n->n.getPins().size()>0);
    }

    @Override
    protected Stream<SitePinInst> getNetPorts(Net net) {
        return net.getPins().stream();
    }

    @Override
    protected boolean isOutputPort(SitePinInst sitePinInst) {
        return sitePinInst.isOutPin();
    }

    @Override
    protected boolean isOutputPortTemplate(Void port) {
        throw new IllegalStateException("Does not have templates");
    }

    @Override
    protected String getInstanceName(Cell cell) {
        String loc = cell.getSiteInst() != null ? " at " + cell.getSiteInst() : "";
        return cell.getName()+" ("+cell.getType()+loc+")";
    }

    @Override
    protected String getPortName(SitePinInst sitePinInst) {
        return sitePinInst.getName();
    }

    @Override
    protected String getPortTemplateName(Void port) {
        throw new IllegalStateException("Does not have templates");
    }

    @Override
    protected Stream<SitePinInst> getRootPorts(Design design) {
        return Stream.empty();
    }

    @Override
    protected String getNetName(Net net) {
        return net.getName();
    }

    @Override
    protected Map<?, ?> getInstanceProperties(Cell cell, Design design) {
        return cell.getProperties();
    }


    public static void dump(Path to, Design design) {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(to))) {
            new DotPhysicalDumper().doDump(design, pw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getDebug(Net net) {
        return net.isStaticNet() ? "static" : "regular";
    }
}
