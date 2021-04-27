package com.xilinx.rapidwright.placer.blockplacer;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DotGraphDumper;
import com.xilinx.rapidwright.design.ModuleImplsInstance;

public class DotModuleImplsDumper extends DotGraphDumper<ModuleImplsInstance, ImplsInstancePort, Void, ImplsPath, DotModuleImplsDumper.ModuleImplsDumpData> {
    public DotModuleImplsDumper(boolean makeNetNode) {
        super(makeNetNode);
    }

    @Override
    protected Stream<ModuleImplsInstance> getInstances(ModuleImplsDumpData design) {
        return design.modules.stream();
    }

    @Override
    protected Stream<ImplsInstancePort> getPorts(ModuleImplsInstance instance, ModuleImplsDumpData design) {
        return design.modulesToPaths.get(instance).stream().flatMap(p->p.ports.stream())
                .filter(port->port instanceof ImplsInstancePort.InstPort && ((ImplsInstancePort.InstPort) port).getInstance() == instance);
    }

    @Override
    protected Stream<Void> getPortTemplates(ModuleImplsInstance instance) {
        return Stream.empty();
    }

    @Override
    protected Stream<ImplsPath> getNets(ModuleImplsDumpData design) {
        return design.paths.stream();
    }

    @Override
    protected Stream<ImplsInstancePort> getNetPorts(ImplsPath net) {
        return net.ports.stream();
    }

    @Override
    protected boolean isOutputPort(ImplsInstancePort port) {
        return port.isOutputPort();
    }

    @Override
    protected boolean isOutputPortTemplate(Void port) {
        throw new UnsupportedOperationException("Should not be called as we have no templates");
    }

    @Override
    protected String getInstanceName(ModuleImplsInstance instance) {
        return instance.getName();
    }

    @Override
    protected String getPortName(ImplsInstancePort port) {
        return port.getName();
    }

    @Override
    protected String getPortTemplateName(Void port) {
        throw new UnsupportedOperationException("Should not be called as we have no templates");
    }

    @Override
    protected Stream<ImplsInstancePort> getRootPorts(ModuleImplsDumpData design) {
        return design.paths.stream().flatMap(path->path.ports.stream()).filter(port->port instanceof ImplsInstancePort.SPI);
    }

    @Override
    protected String getNetName(ImplsPath net) {
        return net.getName();
    }

    @Override
    protected Map<?, ?> getInstanceProperties(ModuleImplsInstance instance) {
        return null;
    }

    @Override
    protected String getDebug(ImplsPath net) {
        return null;
    }

    public static class ModuleImplsDumpData {
        final Design design;
        final List<ModuleImplsInstance> modules;
        final Collection<ImplsPath> paths;
        final Map<ModuleImplsInstance, Set<ImplsPath>> modulesToPaths;


        public ModuleImplsDumpData(Design design, List<ModuleImplsInstance> modules, Collection<ImplsPath> paths, Map<ModuleImplsInstance, Set<ImplsPath>> modulesToPaths) {
            this.design = design;
            this.modules = modules;
            this.paths = paths;
            this.modulesToPaths = modulesToPaths;
        }
    }

    @Override
    protected void doDump(ModuleImplsDumpData design, PrintWriter ps) {
        super.doDump(design, ps);
    }
}
