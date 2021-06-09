package com.xilinx.rapidwright.placer.blockplacer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.AbstractModuleInst;
import com.xilinx.rapidwright.device.Tile;

public abstract class AbstractPath<PortT, ModuleInstT extends AbstractModuleInst<?,?>> implements Iterable<PortT> {

    protected List<PortT> ports = new ArrayList<>();
    protected Set<ModuleInstT> moduleInsts = new HashSet<>();

    /**
     *
     */
    private static final long serialVersionUID = 4016705713685431809L;


    public abstract int getLength();

    public int getSize(){
        return ports.size();
    }


    @Override
    public Iterator<PortT> iterator() {
        return ports.iterator();
    }

    public abstract void calculateLength();

    public abstract String getName();

    public abstract Stream<Tile> streamTiles();

    public boolean connectsTo(ModuleInstT hm) {
        return moduleInsts.contains(hm);
    }

    public int countConnectedModules() {
        return moduleInsts.size();
    }
}
