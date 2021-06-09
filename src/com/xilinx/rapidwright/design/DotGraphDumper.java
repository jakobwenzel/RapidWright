package com.xilinx.rapidwright.design;

import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class DotGraphDumper<InstanceT, PortT, PortTemplateT, NetT, DesignT> {
    private final boolean makeNetNode;

    protected DotGraphDumper(boolean makeNetNode) {
        this.makeNetNode = makeNetNode;
    }

    protected abstract Stream<InstanceT> getInstances(DesignT design);
    protected abstract Stream<PortT> getPorts(InstanceT instance, DesignT design);
    protected abstract Stream<PortTemplateT> getPortTemplates(InstanceT instance);
    protected abstract Stream<NetT> getNets(DesignT design);
    protected abstract Stream<PortT> getNetPorts(NetT net);
    protected abstract boolean isOutputPort(PortT port);
    protected abstract boolean isOutputPortTemplate(PortTemplateT port);
    protected abstract String getInstanceName(InstanceT instance);
    protected abstract String getPortName(PortT port);
    protected abstract String getPortTemplateName(PortTemplateT port);
    protected abstract Stream<PortT> getRootPorts(DesignT design);
    protected abstract String getNetName(NetT net);
    protected abstract Map<?, ?> getInstanceProperties(InstanceT instance, DesignT design);

    private String escapeText(String s) {
        return s.replaceAll("([\\\\\"<>])", "\\\\$1");
    }
    private String escapeHtml(String s) {
        return s.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }


    private String formatPorts(List<PortT> ports, List<PortTemplateT> missingPorts, Map<PortT, String> portIds) {
        Stream<String> actualPorts = ports.stream().map(p -> "<td border=\"1\" port=\"" + portIds.get(p) + "\">" + escapeHtml(getPortName(p)) + "</td>");
        Stream<String> missingPortStream = missingPorts.stream().map(p -> "<td border=\"1\" bgcolor=\"gray\">" + escapeHtml("MISSING " + getPortTemplateName(p)) + "</td>");

        String res = Stream.concat(actualPorts, missingPortStream)
                .collect(Collectors.joining("\n"));
        if (res.isEmpty()) {
            return "<tr><td border=\"1\">&nbsp;</td></tr>";
        }
        return "<tr><td><table cellspacing=\"0\" cellpadding=\"0\" border=\"0\"><tr>\n"+res+"\n</tr></table></td></tr>";
    }

    private String replaceChars(String name) {
        return name.replaceAll("[^0-9a-zA-Z_]","");
    }
    private Map<PortT, String> dumpInstance(PrintWriter ps, java.util.PrimitiveIterator.OfInt ids, InstanceT inst, DesignT design) {
        String id = "inst_" +replaceChars(getInstanceName(inst))+"_"+ ids.next();


        Map<Boolean, List<PortT>> partitioned = getPorts(inst, design)
                .sorted(Comparator.comparing(this::getPortName))
                .collect(Collectors.partitioningBy(this::isOutputPort));
        List<PortT> inputs = partitioned.get(false);
        List<PortT> outputs = partitioned.get(true);

        Set<String> portNames = Stream.concat(inputs.stream(), outputs.stream())
                .map(this::getPortName)
                .collect(Collectors.toSet());

        Map<Boolean, List<PortTemplateT>> partitionedTemplates = getPortTemplates(inst)
                .sorted(Comparator.comparing(this::getPortTemplateName))
                .filter(p -> !portNames.contains(getPortTemplateName(p)))
                .collect(Collectors.partitioningBy(this::isOutputPortTemplate));

        List<PortTemplateT> missingInputs = partitionedTemplates.get(false);
        List<PortTemplateT> missingOutputs = partitionedTemplates.get(true);

        Iterator<String> portIdIter = IntStream.iterate(0, i -> i + 1).mapToObj(Integer::toString).iterator();
        Map<PortT, String> portIds = Stream.concat(inputs.stream(), outputs.stream())
                .collect(Collectors.toMap(Function.identity(), p -> portIdIter.next()));


        Stream<String> head = Stream.of(
                formatPorts(inputs, missingInputs, portIds),
                "<tr><td border=\"1\"><b>"+escapeHtml(getInstanceName(inst))+"</b></td></tr>"
        );
        Stream<String> props;
        Map<?,?> propMap = getInstanceProperties(inst, design);
        if (propMap == null) {
            props = Stream.empty();
        } else {
            props = propMap
                    .entrySet()
                    .stream()
                    .map(e -> "<tr><td border=\"1\">"+escapeHtml(e.getKey() + " -&gt; " + e.getValue())+"</td></tr>")
                    .sorted();
        }
        Stream<String> tail = Stream.of(
                formatPorts(outputs, missingOutputs, portIds)
        );

        String label = Stream.of(
                head,
                props,
                tail
        )
                .flatMap(s->s)
                .collect(Collectors.joining("\n","<table cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\n","\n</table>"));

        ps.println(id+"[label=<\n"+label+">, shape=none];");


        return portIds.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e->id+":"+e.getValue()));
    }

    private Map<PortT,String> dumpRootPins(PrintWriter ps, DesignT design) {


        Iterator<String> portIdIter = IntStream.iterate(0, i -> i + 1).mapToObj(i -> "rootPort" + i).iterator();
        Map<PortT, String> portIds = getRootPorts(design)
                .collect(Collectors.toMap(Function.identity(), p -> portIdIter.next()));
        portIds.forEach((p,id)-> {
            ps.println(id+"[label=\"ROOT PIN "+escapeText(getPortName(p))+"\"];");
        });
        return portIds;
    }

    private Map<PortT, String> dumpCells(DesignT design, PrintWriter ps) {
        PrimitiveIterator.OfInt ids = IntStream.iterate(0, i -> i + 1).iterator();
        return getInstances(design)
                .map(inst -> {
                    return dumpInstance(ps, ids, inst, design);
                })
        .reduce(dumpRootPins(ps, design), (m, n) -> {m.putAll(n); return m;});
    }

    private List<PortT> getNetSources(NetT net) {
        List<PortT> sources = getNetPorts(net).filter(this::isOutputPort).collect(Collectors.toList());
        if (sources.size() > 1) {
            System.err.println("multiple sources for "+net+": "+sources);
        }
        if (sources.isEmpty()) {
            System.err.println("no source for "+net +" "+getDebug(net));
        }
        return sources;
    }

    private void dumpConnections(DesignT design, Map<PortT, String> portIds, PrintWriter ps) {
        Iterator<String> netIdIter = IntStream.iterate(0, i -> i + 1).mapToObj(i -> "net" + i).iterator();
        if (makeNetNode) {

            getNets(design)
                    .forEach(net -> {
                        String nid = netIdIter.next();
                        ps.println(nid+"[label=\"NET "+escapeText(getNetName(net))+"\"];");

                        getNetPorts(net).forEach(port -> {
                            String pid = portIds.get(port);
                            if (pid == null) {
                                System.err.println("no id for port "+port+" in "+net);
                            } else {
                                if (isOutputPort(port)) {
                                    ps.println(pid + " -> " + nid);
                                } else {
                                    ps.println(nid + " -> " + pid);
                                }
                            }
                        });

                    });
        } else {
            getNets(design)
                    .forEach(net -> {
                        List<PortT> sources = getNetSources(net);
                        for (PortT source : sources) {
                            String sourceId = portIds.get(source);
                            if (sourceId == null) {
                                String nid = netIdIter.next();
                                ps.println(nid+"[label=\"NET unknown Source of "+escapeText(getNetName(net))+"\"];");

                                sourceId = nid;

                                System.err.println("no id for source " + source + " in net " + net);
                            }
                            String finalSourceId = sourceId;
                            getNetPorts(net).filter(p -> p != source)
                                    .forEach(sink -> {
                                        String sinkId = portIds.get(sink);
                                        if (sinkId == null) {
                                            System.err.println("no id for sink " + sink + " in net " + net + " " + getDebug(net));
                                        } else {
                                            ps.println(finalSourceId + " -> " + sinkId + ";");
                                        }
                                    });

                        }
                    });
        }

    }

    protected abstract String getDebug(NetT net);

    protected void doDump(DesignT design, PrintWriter ps) {
        ps.println("digraph G {");

        Map<PortT, String> portIds = dumpCells(design, ps);

        dumpConnections(design, portIds, ps);

        ps.println("}");
    }
}
