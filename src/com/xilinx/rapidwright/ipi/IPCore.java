package com.xilinx.rapidwright.ipi;

import java.nio.file.Path;
import java.util.Objects;

public class IPCore {
    private final String name;
    private final String hash;
    private final String instName;
    private final Path verilogImplementation;

    public IPCore(String name, String hash, String instName, Path verilogImplementation) {
        this.name = Objects.requireNonNull(name);
        this.hash = Objects.requireNonNull(hash);
        this.instName = Objects.requireNonNull(instName);
        this.verilogImplementation = verilogImplementation;
    }

    public String getHash() {
        return hash;
    }

    public String getName() {
        return name;
    }

    public Path getVerilogImplementation() {
        return verilogImplementation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IPCore ipCore = (IPCore) o;
        return hash.equals(ipCore.hash) && name.equals(ipCore.name) && verilogImplementation.equals(ipCore.verilogImplementation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, name, verilogImplementation);
    }

    public String getInstName() {
        return instName;
    }

    @Override
    public String toString() {
        return "IPCore{" +
                "name='" + name + '\'' +
                ", hash='" + hash + '\'' +
                ", instName='" + instName + '\'' +
                ", verilogImplementation=" + verilogImplementation +
                '}';
    }
}
