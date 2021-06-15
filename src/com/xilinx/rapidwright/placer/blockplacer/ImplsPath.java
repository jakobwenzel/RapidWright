package com.xilinx.rapidwright.placer.blockplacer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.SimpleTileRectangle;
import com.xilinx.rapidwright.device.Tile;

public class ImplsPath extends AbstractPath<ImplsInstancePort, ModuleImplsInstance>{
    int length;

    public ImplsPath(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public void addPort(ImplsInstancePort port) {
        ports.add(port);
        if (port instanceof ImplsInstancePort.InstPort) {
            moduleInsts.add(((ImplsInstancePort.InstPort) port).getInstance());
        }
        port.setPath(this);
    }


    final String name;


    @Override
    public int getLength() {
        /*int oldLength = length;
        calculateLength();
        if (length != oldLength) {
            throw new RuntimeException("wrongly cached length!");
        }*/
        return length;
    }

    public void calculateLength(){

        SimpleTileRectangle rect = new SimpleTileRectangle();
        for (ImplsInstancePort port : ports) {
            port.enterToRect(rect);
        }

        if (rect.isEmpty()) {
            length = 0;
            return;
        }

        int fanOutPenalty = 1;
        if (getSize() > 30){
            fanOutPenalty = 3;
        }

        length = rect.hpwl() * fanOutPenalty;


        /*Optional<TileRectangle> collect = ports.stream()
                .flatMap(ImplsInstancePort::streamTiles)
                .collect(TileRectangle.collector());
        if (collect.isPresent()) {
            if (!collect.get().equals(immutableRect)){
                throw new RuntimeException("Methods differ, "+immutableRect+" vs "+collect.get()+" at "+name);
            }
        }
        int legacyCalc = collect
                .map(TileRectangle::hpwl)
                .orElse(0);

        if (legacyCalc != length) {
            throw new RuntimeException("Methods differ, "+newHpwl+" vs "+length+" at "+name);
        }*/
    }

    public String getName() {
        return name;
    }

    @Override
    public Stream<Tile> streamTiles() {
        return ports.stream().flatMap(ImplsInstancePort::streamTiles);
    }

    public ImplsInstancePort findSource() {
        final List<ImplsInstancePort> sources = ports.stream().filter(ImplsInstancePort::isOutputPort).collect(Collectors.toList());
        if (sources.size()>1) {
            throw new IllegalStateException("Multiple sources at " + getName() + ": " + sources);
        } else if (sources.isEmpty()) {
            return null;
        }
        return sources.get(0);
    }
}
