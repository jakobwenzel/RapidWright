package com.xilinx.rapidwright.summer20;

import java.util.Map;

import com.trolltech.qt.core.QRectF;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QPainter;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.RelocatableTileRectangle;
import com.xilinx.rapidwright.design.TileRectangle;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.gui.TileScene;

public class TileDataScene<T> extends TileScene {
    private Map<Tile, T> tileData;

    public TileDataScene(Design design, Map<Tile, T> tileData) {
        super(design, false, true);
        this.tileData = tileData;

    }

    public Map<Tile, T> getTileData() {
        return tileData;
    }

    public void setTileData(Map<Tile, T> tileData) {
        this.tileData = tileData;
    }

    @Override
    public void drawBackground(QPainter painter, QRectF rect) {
        super.drawBackground(painter, rect);

        painter.setPen(QColor.white);
        tileData.forEach((tile, data) -> {
            String s = data != null ? data.toString() : "n";
            painter.drawText(
                    tileSize * tile.getColumn(),
                    tileSize * tile.getRow(),
                    tileSize,
                    tileSize,
                    Qt.AlignmentFlag.createQFlags(Qt.AlignmentFlag.AlignVCenter, Qt.AlignmentFlag.AlignCenter).value(),
                    s
            );

        });
        final RelocatableTileRectangle collect = tileData.keySet().stream().collect(TileRectangle.collector());
        painter.setPen(QColor.red);
        painter.drawRect(
                collect.getMinColumn() * tileSize,
                (collect.getMinRow()) * tileSize,
                (collect.getWidth()+1) * tileSize,
                (collect.getHeight()+1) * tileSize
                );
    }
}
