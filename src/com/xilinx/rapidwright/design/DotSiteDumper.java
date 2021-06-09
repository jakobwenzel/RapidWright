package com.xilinx.rapidwright.design;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xilinx.rapidwright.device.BEL;
import com.xilinx.rapidwright.device.BELPin;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Site;

public class DotSiteDumper extends DotGraphDumper<BEL, BELPin, BELPin, List<BELPin>, SiteInst>{
    private final Predicate<BEL> includeBel;
    public DotSiteDumper(Predicate<BEL> includeBel) {
        super(false);
        if (includeBel == null) {
            this.includeBel = x -> true;
        } else {
            this.includeBel = includeBel;
        }
    }

    private final Map<Site, List<List<BELPin>>> netCache = new HashMap<>();

    private Stream<List<BELPin>> toConnections(BELPin pin) {
        if (!pin.isOutput()) {
                return Stream.empty();
        }
        return Stream.of(
                Stream.concat(Stream.of(pin), pin.getSiteConns().stream().filter(p->includeBel.test(p.getBEL()))).collect(Collectors.toList())
        );
    }
    private List<List<BELPin>> computeSiteInfo(Site site) {
        return Arrays.stream(site.getBELs())
                .filter(this.includeBel)
                .flatMap(b -> Arrays.stream(b.getPins()))
                .flatMap(this::toConnections)
                .filter(n->n.size()>1)
                .filter(n->n.stream().noneMatch(p->p.getName().equals("O7")))
                .collect(Collectors.toList());
    }

    private List<List<BELPin>> getSiteInfo(Site site) {
        return netCache.computeIfAbsent(site, this::computeSiteInfo);
    }

    @Override
    protected Stream<BEL> getInstances(SiteInst design) {
        return Arrays.stream(design.getBELs()).filter(includeBel);
    }

    @Override
    protected Stream<BELPin> getPorts(BEL instance, SiteInst design) {
        return Arrays.stream(instance.getPins());
    }

    @Override
    protected Stream<BELPin> getPortTemplates(BEL instance) {
        return Arrays.stream(instance.getPins());
    }

    @Override
    protected Stream<List<BELPin>> getNets(SiteInst design) {
        return getSiteInfo(design.getSite()).stream();
    }

    @Override
    protected Stream<BELPin> getNetPorts(List<BELPin> net) {
        return net.stream();
    }

    @Override
    protected boolean isOutputPort(BELPin port) {
        return port.isOutput();
    }

    @Override
    protected boolean isOutputPortTemplate(BELPin port) {
        return port.isOutput();
    }

    @Override
    protected String getInstanceName(BEL instance) {
        return instance.getName();
    }

    @Override
    protected String getPortName(BELPin port) {
        return port.getName();
    }

    @Override
    protected String getPortTemplateName(BELPin port) {
        return port.getName();
    }

    @Override
    protected Stream<BELPin> getRootPorts(SiteInst design) {
        return Stream.empty();
    }

    @Override
    protected String getNetName(List<BELPin> net) {
        return net.get(0).getSiteWireName();
    }

    @Override
    protected Map<?, ?> getInstanceProperties(BEL instance, SiteInst design) {
        final Cell cell = design.getCell(instance);
        if (cell == null) {
            return null;
        }
        return cell.getProperties();
    }

    @Override
    protected String getDebug(List<BELPin> net) {
        return null;
    }


    public static void dump(Path to, SiteInst siteInst, Predicate<BEL> includeBel) {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(to))) {
            new DotSiteDumper(includeBel).doDump(siteInst, pw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void dump(Path to, Site site, Predicate<BEL> includeBel) {
        SiteInst temp = new SiteInst("", site.getSiteTypeEnum());
        temp.place(site);
        dump(to, temp, includeBel);
    }

    public static void main(String[] args) {
        final Device dev = Device.getDevice(args[0]);
        final Site site = dev.getSite(args[1]);
        if (site==null) {
            throw new RuntimeException("Could not find site "+args[1]+" in device "+dev.getName());
        }


        Set<BEL> belsToShow = new HashSet<>();
        Set<BELPin> visited = new HashSet<>();
        Queue<BELPin> pins = new ArrayDeque<>();
        pins.add(site.getBELPin("CARRY8", "CO7"));

        while (!pins.isEmpty()) {
            BELPin pin = pins.poll();
            System.out.println("adding "+pin);

            if (!visited.add(pin)) {
                continue;
            }

            belsToShow.add(pin.getBEL());

            if (pin.isOutput()) {
                pins.addAll(pin.getSiteConns());
            } else {
                pins.addAll(Arrays.asList(pin.getBEL().getPins()));
            }
        }

        Predicate<BEL> includeBel = belsToShow::contains;

        dump(Paths.get(args[2]), site, includeBel);
    }
}
