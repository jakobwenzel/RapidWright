package com.xilinx.rapidwright.placer.blockplacer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.Tile;

public class BlockPlacer2ModuleDebug extends BlockPlacer2Module{
    private final int maxTileColumn;

    public BlockPlacer2ModuleDebug(Design design, int maxTileColumn) {
        super(design);
        this.maxTileColumn = maxTileColumn;
    }

    Map<com.xilinx.rapidwright.design.Module, Collection<Site>> placementCache = new HashMap<>();
    @Override
    Collection<Site> getAllPlacements(HardMacro hm) {
            return placementCache.computeIfAbsent(hm.getModule(), mod -> mod.getAllValidPlacements().stream()
                    .filter(s -> s.getTile().getColumn() <= maxTileColumn)
                    .collect(Collectors.toList()));
    }

    @Override
    protected boolean isInRange(Site current, Site newPlacement) {
        if (newPlacement.getTile().getColumn() > maxTileColumn) {
            return false;
        }
        return super.isInRange(current, newPlacement);
    }

    @Override
    protected HashSet<Tile> isValidPlacement(ModuleInst modInst, Site anchorSite, Tile proposedAnchorTile, HashSet<Tile> usedTiles) {
        if (proposedAnchorTile.getColumn()>maxTileColumn) {
            return null;
        }
        return super.isValidPlacement(modInst, anchorSite, proposedAnchorTile, usedTiles);
    }
}
