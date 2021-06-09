package com.xilinx.rapidwright.design;

import java.util.stream.Collector;

import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.Tile;

/**
 * A Rectangle of tiles, i.e. a Bounding Box around some Set of Tiles.
 *
 * The tiles at the edge of the rectangle (e.g. at minX/minY and maxX/maxY) are all assumed to be inside the rectangle.
 * For both X and Y: min <= Tiles <= max
 *
 * The way to store tiles is set by subclasses. Depending on the storage method, they may or may not be relocatable
 */
public abstract class TileRectangle {
    public static Collector<Tile, ?, RelocatableTileRectangle> collector() {
        return RelocatableTileRectangle.collector();
    }

    public abstract int getMinRow();

    public abstract int getMaxRow();

    public abstract int getMinColumn();

    public abstract int getMaxColumn();


    public abstract boolean isEmpty();

    public abstract void extendTo(Tile tile);

    /**
     * Check whether a tile is contained in the Rectangle
     *
     * @param tile the tile to check
     * @return true if it is inside
     */
    public boolean isInside(Tile tile) {
        return (tile.getColumn() >= getMinColumn() && tile.getColumn() <= getMaxColumn() &&
                tile.getRow() >= getMinRow() && tile.getRow() <= getMaxRow());
    }


    public int hpwl() {
        return getMaxColumn() - getMinColumn() + getMaxRow() - getMinRow();
    }


    private static boolean intervalOverlaps(int minA, int maxA, int minB, int maxB) {
        return minA <= maxB && minB <= maxA;
    }

    /**
     * Check whether this Rectangle has any Tiles in common with another one
     *
     * @param other Rectangle to check
     * @return true if there is any overlap
     */
    public boolean overlaps(TileRectangle other) {
        return intervalOverlaps(getMinColumn(), getMaxColumn(), other.getMinColumn(), other.getMaxColumn())
                && intervalOverlaps(getMinRow(), getMaxRow(), other.getMinRow(), other.getMaxRow());
    }


    public int getWidth() {
        return getMaxColumn() - getMinColumn();
    }

    public int getHeight() {
        return getMaxRow() - getMinRow();
    }

    public int getLargerDimension() {
        return Math.max(getWidth(), getHeight());
    }

    public abstract void extendToCorresponding(RelocatableTileRectangle rect, Site currentAnchor, SiteInst templateAnchor);


    /**
     * Extend the Rectangle so that a shifted tile is inside. The Tile is assumed to be located relative to some anchor.
     * The anchor is shifted from {@code templateAnchor} to {@code currentAnchor}. This location relative to the new
     * anchor is then included in the Rectangle.
     *
     * @param tile           tile to include after shifting
     * @param currentAnchor  target anchor
     * @param templateAnchor source anchor
     */
    public void extendToCorresponding(Tile tile, Site currentAnchor, SiteInst templateAnchor) {
        Tile corresponding = Module.getCorrespondingTile(tile, currentAnchor.getTile(), templateAnchor.getTile());
        extendTo(corresponding);
    }
}
