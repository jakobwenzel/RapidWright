package com.xilinx.rapidwright.summer20;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.trolltech.qt.core.QRect;
import com.trolltech.qt.core.QRectF;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QBrush;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QFont;
import com.trolltech.qt.gui.QPainter;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.TileRectangle;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.gui.TileScene;

public class PblockScene extends TileScene {
    private List<UiPBlock> blocks;

    public PblockScene(Design design) {
        super(design, false, true);
        this.blocks = new ArrayList<>();
    }

    public List<UiPBlock> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<UiPBlock> blocks) {
        this.blocks = blocks;
    }

    @Override
    protected void drawFPGAFabric(QPainter painter) {
        super.drawFPGAFabric(painter);
        drawIntArrows(painter);
        //drawBlocks(painter);
    }

    private void drawIntArrows(QPainter painter) {
        painter.setPen(QColor.white);
        for (Tile tile: device.getAllTiles()) {
            Arrays.stream(tile.getSites())
                    .flatMap(s-> {
                        final Tile intTile = s.getIntTile();
                        if (intTile == null) {
                            return Stream.empty();
                        }
                        return Stream.of(intTile);
                    })
                    .distinct()
                    .forEach(intTile -> {
                       painter.drawLine(
                               tile.getColumn() * tileSize + tileSize/2,
                               tile.getRow() * tileSize + tileSize/2,
                               intTile.getColumn() * tileSize + tileSize/2,
                               intTile.getRow() * tileSize + tileSize/2
                       );
                    });
        }
    }

    @Override
    public void drawBackground(QPainter painter, QRectF rect) {
        super.drawBackground(painter, rect);

        drawBlocks(painter);
    }

    /*private QColor getColor(Pair<? extends TileRectangle, String> block, int i) {
        QColor[] colors = {QColor.red, QColor.green, QColor.blue, QColor.yellow};
        //final Iterator<QColor> color = IntStream.iterate(0, i -> i + 1).map(i -> i % colors.length).mapToObj(i -> colors[i]).iterator();

        Random rnd = new Random((long) block.getSecond().hashCode() * i);
        return colors[rnd.nextInt(colors.length)];
    }*/



    abstract class ForAllRects {
        abstract void doPaint(UiPBlock block , int i, QRect rect);
        void run() {
            for (int i = 0; i < blocks.size(); i++) {

                final UiPBlock block = blocks.get(i);
                final TileRectangle rect = block.rect;
                final QRect qRect = tileRectToQRect(rect);

                doPaint(block, i ,qRect);
            }

        }
    }

    private void drawBlocks(QPainter painter) {

        final QFont font = painter.font().clone();
        font.setPointSize(font.pointSize()*8);
        painter.setFont(font);


        //We draw all backgrounds, all outlines and then all texts to better support overlapped pblocks.

        //Backgrounds
        new ForAllRects(){
            @Override
            void doPaint(UiPBlock block, int i, QRect rect) {
                final QColor transparent = block.color.clone();
                transparent.setAlpha(90);
                painter.fillRect(rect, new QBrush(transparent));
            }
        }.run();

        new ForAllRects() {
            @Override
            void doPaint(UiPBlock block, int i, QRect rect) {
                painter.setPen(block.color);
                painter.drawRect(rect);
            }
        }.run();
        new ForAllRects() {
            @Override
            void doPaint(UiPBlock block, int i, QRect rect) {
                painter.setPen(block.color);

                painter.drawText(
                        rect,
                        Qt.AlignmentFlag.createQFlags(Qt.AlignmentFlag.AlignVCenter, Qt.AlignmentFlag.AlignCenter).value(),
                        block.name
                );
            }
        }.run();

    }
}
