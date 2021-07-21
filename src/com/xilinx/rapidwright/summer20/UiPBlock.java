package com.xilinx.rapidwright.summer20;

import com.trolltech.qt.gui.QColor;
import com.xilinx.rapidwright.design.TileRectangle;

public class UiPBlock {
    public final TileRectangle rect;
    public final String name;
    public final QColor color;

    public UiPBlock(TileRectangle rect, String name, QColor color) {
        this.rect = rect;
        this.name = name;
        this.color = color;
    }
}
