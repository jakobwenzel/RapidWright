package com.xilinx.rapidwright.ipi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XDCConstraints {
    private Map<String, PackagePinConstraint> pinConstraints = new HashMap<>();
    private Map<String, ClockConstraint> clockConstraints = new HashMap<>();
    private Map<String, Map<String, String>> cellProperties = new HashMap<>();
    private List<String> unsupportedConstraints = new ArrayList<>();


    public XDCConstraints(Map<String, PackagePinConstraint> pinConstraints,
                          Map<String, ClockConstraint> clockConstraints,
                          Map<String, Map<String, String>> cellProperties,
                          List<String> unsupportedConstraints) {
        this.pinConstraints = pinConstraints;
        this.clockConstraints = clockConstraints;
        this.cellProperties = cellProperties;
        this.unsupportedConstraints = unsupportedConstraints;
    }

    public XDCConstraints() {
    }

    public Map<String, PackagePinConstraint> getPinConstraints() {
        return pinConstraints;
    }

    public Map<String, ClockConstraint> getClockConstraints() {
        return clockConstraints;
    }

    public Map<String, Map<String, String>> getCellProperties() {
        return cellProperties;
    }

    public List<String> getUnsupportedConstraints() {
        return unsupportedConstraints;
    }
}
