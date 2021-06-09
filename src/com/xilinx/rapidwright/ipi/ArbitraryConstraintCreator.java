package com.xilinx.rapidwright.ipi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;

import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.IOBank;
import com.xilinx.rapidwright.device.IOBankType;
import com.xilinx.rapidwright.device.Package;
import com.xilinx.rapidwright.device.PackagePin;
import com.xilinx.rapidwright.device.Part;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFPort;

public class ArbitraryConstraintCreator {

    private static void placePin(EDIFPort port, String name, Iterator<PackagePin> pins, HashMap<String, PackagePinConstraint> result, Package pkg) {

        if (!pins.hasNext()) {
            throw new RuntimeException("Package does not have enough pins for design!");
        }
        PackagePin pin = pins.next();

        PackagePinConstraint constraint = new PackagePinConstraint();
        constraint.setName(pin.getName());

        constraint.setIOStandard("LVCMOS25");
        result.put(name, constraint);
    }

    private static boolean isPinUsable(PackagePin pin) {
        if (!pin.isGeneralPurpose()) {
            return false;
        }

        IOBank bank = Objects.requireNonNull(pin.getIOBank());
        return bank.getBankType() != IOBankType.BT_HIGH_PERFORMANCE;
    }

    public static XDCConstraints createArbitraryConstraints(EDIFCell topCell, Part part, Device device) {
        HashMap<String, PackagePinConstraint> result = new HashMap<>();
        Package pkg = device.getPackage(part.getPkg());

        final long count = pkg.getPackagePinMap().values().stream()
                .filter(ArbitraryConstraintCreator::isPinUsable)
                .count();
        System.out.println("device has "+count+" usable pins");

        long total = 0;
        long generalPurpose = 0;
        long hp = 0;
        long supply = 0;

        for (PackagePin pin : pkg.getPackagePinMap().values()) {

            total++;
            if (pin.isGeneralPurpose()) {
                generalPurpose++;
            }

            if (pin.getPinFunction().equals("GN") || pin.getPinFunction().startsWith("VCC")) {
                supply++;
            } else if (pin.getIOBank() == null) {
                System.out.println("no IO Bank for "+pin+", "+pin.getPinFunction()+", "+pin.isGeneralPurpose()+", "+pin.isLowCap());
            } else {
                IOBank bank = Objects.requireNonNull(pin.getIOBank());
                if (bank.getBankType() == IOBankType.BT_HIGH_PERFORMANCE) {
                    hp++;
                }
            }
        }
        System.out.println("total = " + total);
        System.out.println("generalPurpose = " + generalPurpose);
        System.out.println("hp = " + hp);
        System.out.println("supply = " + supply);

        Iterator<PackagePin> pins = pkg.getPackagePinMap().values().stream()
                .filter(ArbitraryConstraintCreator::isPinUsable)
                .iterator();
        for (EDIFPort port : topCell.getPorts()) {
            if (port.isBus()) {
                for (int bit: port.getBitBlastedIndicies()) {
                    placePin(port, port.getBusName()+'['+bit+']', pins, result, pkg);
                }
            } else {
                placePin(port, port.getBusName(), pins, result, pkg);
            }
        }

        return new XDCConstraints(result, new HashMap<>(), new HashMap<>(), new ArrayList<>());
    }
}
