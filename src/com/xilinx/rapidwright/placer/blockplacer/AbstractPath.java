package com.xilinx.rapidwright.placer.blockplacer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import com.xilinx.rapidwright.device.Tile;

public abstract class AbstractPath<PortT> implements Iterable<PortT> {

    protected List<PortT> ports = new ArrayList<>();

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
}
