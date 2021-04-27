package com.xilinx.rapidwright.design;

import java.util.Objects;

import com.xilinx.rapidwright.device.Site;

public class ModulePlacement {
    public final int implementationIndex;
    public final Site placement;

    public ModulePlacement(int implementationIndex, Site placement) {
        this.implementationIndex = implementationIndex;
        this.placement = Objects.requireNonNull(placement);
    }

    @Override
    public String toString() {
        return "impl "+implementationIndex+" at "+placement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModulePlacement that = (ModulePlacement) o;
        return implementationIndex == that.implementationIndex && placement.equals(that.placement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(implementationIndex, placement);
    }
}
