package com.xilinx.rapidwright.examples;

/*
 * Original work: Copyright (c) 2010-2011 Brigham Young University
 * Modified work: Copyright (c) 2017 Xilinx, Inc.
 * All rights reserved.
 *
 * Author: Chris Lavin, Xilinx Research Labs.
 *
 * This file is part of RapidWright.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QMainWindow;
import com.trolltech.qt.gui.QStatusBar;
import com.trolltech.qt.gui.QWidget;
import com.xilinx.rapidwright.gui.TileScene;
import com.xilinx.rapidwright.gui.TileView;

/**
 * This class is an example of how RapidWright could be used to build
 * interactive tools using Qt or other GUI packages.  This class
 * creates a zoom-able 2D array of the tiles found in the devices installed
 * with RapidWright.  This example requires the Qt Jambi (Qt for Java)
 * jars to run.
 * @author marc
 */
public class TileWindow extends QMainWindow{
    /** This is the Qt View object  */
    private TileView view;
    /** This is the container for the text in the Status Bar at the bottom of the screen */
    private QLabel statusLabel;
    /** This is the Qt Scene object */
    private TileScene scene;


    /**
     * Constructor of a new PartTileBrowser
     * @param parent Parent widget to which this object belongs.
     */
    public TileWindow(QWidget parent, TileScene scene) {
        super(parent);
        setWindowTitle("Tile View");

        this.scene = scene;

        view = new TileView(scene);

        setCentralWidget(view);

        scene.updateStatus.connect(this, "updateStatus()");
        statusLabel = new QLabel("Status Bar");
        statusLabel.setText("Status Bar");
        QStatusBar statusBar = new QStatusBar();
        statusBar.addWidget(statusLabel);
        setStatusBar(statusBar);

    }

    @SuppressWarnings("unused")
    void updateStatus() {
        int x = (int) scene.getCurrX();
        int y = (int) scene.getCurrY();
        if (x >= 0 && x < scene.getDevice().getColumns() && y >= 0 && y < scene.getDevice().getRows()){
            String tileName = scene.getDevice().getTile(y, x).getName();
            statusLabel.setText("Part: "+scene.getDevice().getName().toUpperCase() +"  Tile: "+ tileName+" ("+x+","+y+")");
        }
    }

    public static void showBlocking(TileScene scene) {
        scene.setUseImage(true);
        final TileWindow tileWindow = new TileWindow(null, scene);
        tileWindow.show();
        QApplication.exec();
    }

}

