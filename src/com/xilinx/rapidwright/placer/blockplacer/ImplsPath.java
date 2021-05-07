package com.xilinx.rapidwright.placer.blockplacer;

import java.util.Objects;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.TileRectangle;
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

        TileRectangle.MutableRectangle rect = new TileRectangle.MutableRectangle();
        for (ImplsInstancePort port : ports) {
            port.enterToRect(rect);
        }

        TileRectangle immutableRect = rect.toImmutable().orElseThrow(IllegalStateException::new);

        int fanOutPenalty = 1;
        if (getSize() > 30){
            fanOutPenalty = 3;
        }

        length = immutableRect.hpwl() * fanOutPenalty;


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
        //TODO fanout penalty?
    }

    public String getName() {
        return name;
    }

    @Override
    public Stream<Tile> streamTiles() {
        return ports.stream().flatMap(ImplsInstancePort::streamTiles);
    }
}
