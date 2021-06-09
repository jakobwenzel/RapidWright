package com.xilinx.rapidwright.placer.blockplacer;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleImpls;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.ModulePlacement;

public class BlockPlacer2ImplsDebug extends BlockPlacer2Impls{
        private final int maxTileColumn;

        public BlockPlacer2ImplsDebug(Design design, List<ModuleImplsInstance> instances, java.nio.file.Path graphData, boolean ignoreMostUsedNets, int maxTileColumn) {
            super(design, instances, ignoreMostUsedNets, graphData);
            this.maxTileColumn = maxTileColumn;
        }

        Map<ModuleImpls, Collection<ModulePlacement>> placementCache = new HashMap<>();
        @Override
        Collection<ModulePlacement> getAllPlacements(ModuleImplsInstance hm) {
            return placementCache.computeIfAbsent(hm.getModule(), mod -> mod.getAllPlacements().stream()
                    .filter(s -> s.placement.getTile().getColumn() <= maxTileColumn)
                    .collect(Collectors.toList()));
        }

        @Override
        protected boolean isInRange(ModulePlacement current, ModulePlacement newPlacement) {
            if (newPlacement.placement.getTile().getColumn() > maxTileColumn) {
                return false;
            }
            return super.isInRange(current, newPlacement);
        }

    }