package com.xilinx.rapidwright.placer.blockplacer;

import java.util.Objects;
import java.util.stream.Stream;

import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.Port;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.TileRectangle;
import com.xilinx.rapidwright.device.Tile;

public abstract class ImplsInstancePort {
    public abstract Stream<Tile> streamTiles();

    public abstract String getName();

    public abstract boolean isOutputPort();

    public abstract void enterToRect(TileRectangle.MutableRectangle rect);

    public static class SPI extends ImplsInstancePort {
        private final SitePinInst sitePinInst;

        public SPI(SitePinInst sitePinInst) {
            this.sitePinInst = Objects.requireNonNull(sitePinInst);
        }

        @Override
        public Stream<Tile> streamTiles() {
            return Stream.of(sitePinInst.getTile());
        }

        @Override
        public String getName() {
            return sitePinInst.getSite().getName()+"."+sitePinInst.getName();
        }

        @Override
        public boolean isOutputPort() {
            return sitePinInst.isOutPin();
        }

        @Override
        public void enterToRect(TileRectangle.MutableRectangle rect) {
            rect.extendTo(sitePinInst.getTile());
        }

        public SitePinInst getSitePinInst() {
            return sitePinInst;
        }
    }
    public static class InstPort extends ImplsInstancePort {
        private final ModuleImplsInstance instance;
        private final String port;

        public InstPort(ModuleImplsInstance instance, String port) {
            this.instance = instance;
            this.port = port;
        }

        @Override
        public Stream<Tile> streamTiles() {
            if (instance.getPlacement() == null) {
                return Stream.empty();
            }
            Port portImpl = instance.getCurrentModuleImplementation().getPort(this.port);
            if (portImpl == null) {
                throw new NullPointerException("Did not find "+port+" on "+instance.getCurrentModuleImplementation().getName());
            }

            return portImpl.getSitePinInsts().stream()
                    .map(spi -> instance.getCurrentModuleImplementation().getCorrespondingTile(spi.getTile(), instance.getPlacement().placement.getTile(), instance.getModule().getDevice()));
        }

        @Override
        public String getName() {
            return port;
        }

        @Override
        public boolean isOutputPort() {
            return instance.getModule().get(0).getPort(port).isOutPort();
        }

        @Override
        public void enterToRect(TileRectangle.MutableRectangle rect) {
            if (instance.getPlacement() == null) {
                return;
            }
            Port portImpl = instance.getCurrentModuleImplementation().getPort(this.port);
            TileRectangle boundingBox = portImpl.getBoundingBox();
            /*for (SitePinInst sitePinInst : portImpl.getSitePinInsts()) {
                rect.extendToCorresponding(
                        sitePinInst.getTile(),
                        instance.getPlacement().placement,
                        instance.getCurrentModuleImplementation().getAnchor()
                );

                //Tile tile = instance.getCurrentModuleImplementation().getCorrespondingTile(sitePinInst.getTile(), instance.getPlacement().placement.getTile(), instance.getModule().getDevice());
                //rect.extendTo(tile);
            }*/
            rect.extendToCorresponding(boundingBox, instance.getPlacement().placement, instance.getCurrentModuleImplementation().getAnchor());

        }

        public ModuleImplsInstance getInstance() {
            return instance;
        }

        public String getPort() {
            return port;
        }
    }
}
