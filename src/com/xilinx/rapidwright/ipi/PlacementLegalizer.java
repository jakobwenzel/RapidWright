package com.xilinx.rapidwright.ipi;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.device.ClockRegion;
import com.xilinx.rapidwright.device.Series;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.device.TileTypeEnum;

public class PlacementLegalizer {


    private static Stream<SitePinInst> getNetSources(SiteInst inst, Predicate<SitePinInst> interestingPins) {
        return inst.getSitePinInsts().stream().filter(interestingPins)
                .map(SitePinInst::getNet)
                //Unused clock inputs are connected to static zero, filter them out
                .filter(net->!net.isStaticNet())
                .map(net->net.getSource());
    }

    private static void legalizeClockManager(SiteInst clockManager, Design design) {
        List<ClockRegion> regions = getNetSources(clockManager, spi -> spi.getName().startsWith("CLKIN"))
                .map(source->source.getTile().getClockRegion())
                .distinct()
                .collect(Collectors.toList());

        if (regions.size() == 0) {
            throw new RuntimeException("did not find a region for "+clockManager);
        }
        if (regions.size() > 1) {
            throw new RuntimeException("multiple regions: "+clockManager);
        }

        ClockRegion region = regions.get(0);

        Site site = Arrays.stream(design.getDevice().getAllCompatibleSites(clockManager.getSiteTypeEnum()))
                .filter(s -> s.getTile().getClockRegion() == region)
                .filter(s -> !design.isSiteUsed(s))
                .findAny().orElseThrow(() -> new RuntimeException("did not find placement for " + clockManager + " in " + region));

        System.out.println("placing "+clockManager+" at "+site);
        clockManager.place(site);

    }
    private static void legalizeClockBuffer(SiteInst clockBuffer, Design design) {
        List<Boolean> lowerHalves = getNetSources(clockBuffer, spi -> !spi.isOutPin())
                .map(source -> source.getTile().getClockRegion().getInstanceY() < design.getDevice().getRows() / 2)
                .distinct().collect(Collectors.toList());
        if (lowerHalves.size() != 1) {
            throw  new RuntimeException("not one entry in lower halves!");
        }
        boolean isLowerHalf = lowerHalves.get(0);

        TileTypeEnum tte = isLowerHalf ? TileTypeEnum.CLK_BUFG_BOT_R : TileTypeEnum.CLK_BUFG_TOP_R;

        Site site = Arrays.stream(design.getDevice().getAllCompatibleSites(clockBuffer.getSiteTypeEnum()))
                .filter(s -> !design.isSiteUsed(s))
                .filter(s -> s.getTile().getTileTypeEnum() == tte)
                .findAny().orElseThrow(() -> new RuntimeException("no placement found for " + clockBuffer));
        System.out.println("placing "+clockBuffer+" at "+site);
        clockBuffer.place(site);
    }

    enum LegalizableType {
        ClockManager,
        ClockBuffer,
        Ignore,
        Unknown;

        static LegalizableType getType(SiteTypeEnum ste) {
            if (ste == SiteTypeEnum.MMCM || ste == SiteTypeEnum.MMCME2_ADV || ste == SiteTypeEnum.MMCME3_ADV) {
                return ClockManager;
            }
            if (ste == SiteTypeEnum.BUFG || ste == SiteTypeEnum.BUFGCE) {
                return ClockBuffer;
            }
            if (ste.toString().startsWith("IOB") || ste == SiteTypeEnum.HRIO) {
                return Ignore;
            }
            return Unknown;
        }

    }

    public static void legalizeNonModuleSitePlacements(Design design, List<SiteInst> nonModuleSiteInsts) {
        Map<LegalizableType, List<SiteInst>> byType = nonModuleSiteInsts.stream().collect(Collectors.groupingBy(si->LegalizableType.getType(si.getSiteTypeEnum())));
        List<SiteInst> clockManagers = byType.get(LegalizableType.ClockManager);
        List<SiteInst> clockBuffers = byType.get(LegalizableType.ClockBuffer);
        List<SiteInst> unknown = byType.get(LegalizableType.Unknown);
        if (unknown != null && unknown.size()>0) {
            throw new RuntimeException("non legalizable instances: "+unknown);
        }

        if (!design.getDevice().getSeries().equals(Series.Series7) && (clockManagers != null || clockBuffers != null)) {
            System.out.println("Can only legalize clocking Instances on 7 series, leaving everything as is...");
            return;
        }

        if (clockManagers != null) {
            for (SiteInst clockManager : clockManagers) {
                clockManager.unPlace();
            }
            for (SiteInst clockManager : clockManagers) {
                legalizeClockManager(clockManager, design);
            }
        }
        if (clockBuffers != null) {
            for (SiteInst clockBuffer : clockBuffers) {
                clockBuffer.unPlace();
            }
            for (SiteInst clockBuffer : clockBuffers) {
                legalizeClockBuffer(clockBuffer, design);
            }
        }

    }
}
