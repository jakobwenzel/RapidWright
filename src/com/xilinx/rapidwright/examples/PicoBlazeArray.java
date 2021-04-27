package com.xilinx.rapidwright.examples;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xilinx.rapidwright.design.AbstractModuleInst;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleImpls;
import com.xilinx.rapidwright.design.ModuleImplsInstance;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.ModulePlacement;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.device.TileTypeEnum;
import com.xilinx.rapidwright.device.helper.TileColumnPattern;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFDirection;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.placer.blockplacer.BlockPlacer2;
import com.xilinx.rapidwright.placer.blockplacer.BlockPlacer2ImplsDebug;
import com.xilinx.rapidwright.placer.blockplacer.BlockPlacer2ModuleDebug;
import com.xilinx.rapidwright.placer.handplacer.HandPlacer;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class PicoBlazeArray {

	private static final String CLK = "clk";
	private static final String RST = "reset";

	private static final String PBLOCK_DCP_PREFIX = "pblock";
	private static final String PICOBLAZE_PREFIX = "picoblaze_";
	private static final String TOP_INPUT_PREFIX = "top_input_";
	private static final String TOP_OUTPUT_PREFIX = "top_output_";

	private static final String[] PICOBLAZE_INPUTS = new String[]{
			"input_port_a","input_port_b", "input_port_c", "input_port_d"
		};
	private static final String[] PICOBLAZE_OUTPUTS = new String[]{
			"output_port_w","output_port_x", "output_port_y", "output_port_z"
		};
	private static final int[] CONN_ARRAY = new int[]{-2,-1,1,2};
	private static final int PICOBLAZE_BUS_WIDTH = 8;
	private static final int BRAMS_IN_CLOCK_REGION_HEIGHT = 12;
	
	/**
	 * To make it easier to specify placement, we change 
	 * the anchor to the BRAM instance 
	 * @param m The module whose anchor should be updated
	 */
	public static void updateAnchorToBRAM(Module m){
		for(SiteInst i : m.getSiteInsts()){
			if(i.getSite().getSiteTypeEnum() == SiteTypeEnum.RAMBFIFO36){
				m.setAnchor(i);
				m.calculateAllValidPlacements(m.getDevice());
			}
		}
	}

	static abstract class PicoBlazeArrayCreator<T extends AbstractModuleInst<T>> {
		private int maxTileColumn;

		public List<T> getInstances() {
			return instances;
		}

		private final List<T> instances = new ArrayList<>();

		public int getMaxTileColumn() {
			return maxTileColumn;
		}

		private T getPicoblazeInst(Design design, int x, int y, Map<String, T> instances){
			return instances.get(PICOBLAZE_PREFIX + x + "_" + y);
		}
		protected abstract T createInstance(Design design, String name, Module impl, ModuleImpls impls);
		public Design createDesign(File srcDir, String deviceName, CodePerfTracker t) {

			// Create a new design with references to device and netlist
			Design design = new Design("top", deviceName);
			Device device = design.getDevice();
			EDIFNetlist netlist = design.getNetlist();
			EDIFCell top = netlist.getTopCell();

			// Load pre-implemented modules
			FilenameFilter ff = FileTools.getFilenameFilter(PBLOCK_DCP_PREFIX+"[0-9]+.dcp");
			int implementationCount = srcDir.list(ff).length;
			ModuleImpls picoBlazeImpls = new ModuleImpls();
			//Module[] picoBlazeImpls = new Module[implementationCount];

			EDIFNetlist moduleNetlist = null;
			for(int i=0; i < implementationCount; i++){
				String dcpName = srcDir + File.separator + PBLOCK_DCP_PREFIX+i+".dcp";
				String metaName = srcDir + File.separator + PBLOCK_DCP_PREFIX+i+"_"+i+"_metadata.txt";
				t.stop().start("Loading " + PBLOCK_DCP_PREFIX+i+".dcp");

				Design d;
				if (moduleNetlist == null) {
					d = Design.readCheckpoint(dcpName,CodePerfTracker.SILENT);
					moduleNetlist = d.getNetlist();
				} else {
					d = new Design(moduleNetlist);
					d.updateDesignWithCheckpointPlaceAndRoute(dcpName);
				}

				Module mod = new Module(d, metaName);
				updateAnchorToBRAM(mod);
				netlist.migrateCellAndSubCells(mod.getNetlist().getTopCell());
				picoBlazeImpls.add(mod);
			}

			t.stop().start("Place PicoBlaze modules");


			// Specify placement of picoblaze modules
			TileColumnPattern bramPattern = TileColumnPattern.createTileColumnPattern(Arrays.asList(TileTypeEnum.BRAM));
			int bramColumns = TileColumnPattern.genColumnPatternMap(device).get(bramPattern).size();
			int bramRows = design.getDevice().getNumOfClockRegionRows() * BRAMS_IN_CLOCK_REGION_HEIGHT;

			//Reduce count for debugging
			System.out.println("bramColumns = " + bramColumns);
			System.out.println("bramRows = " + bramRows);
			bramColumns = 1;
			//bramRows /= 2;

			Map<String, T> instances = new HashMap<>();

			maxTileColumn = 0;
			for(int x=0; x < bramColumns; x++){
				// we will skip top and bottom clock region rows to avoid laguna tiles and U-turn routing
				for(int y=BRAMS_IN_CLOCK_REGION_HEIGHT; y < bramRows-BRAMS_IN_CLOCK_REGION_HEIGHT; y++){
					Site bram = device.getSite("RAMB36_X" + x + "Y" + y);
					Module impl = null;
					for(Module m : picoBlazeImpls){
						if(m.isValidPlacement(bram, device, design)){
							impl = m;
							break;
						}
					}
					if(impl == null) continue; // Laguna site

					maxTileColumn = Math.max(maxTileColumn, bram.getTile().getColumn());

					T mi = createInstance(design, "picoblaze_" + x + "_" + y, impl, picoBlazeImpls);

					instances.put(mi.getName(), mi);
					if (mi.getCellInst().getCellType() != impl.getNetlist().getTopCell()) {
						throw new RuntimeException("wut");
					}
					mi.getCellInst().setCellType(impl.getNetlist().getTopCell()); //TODO needed????

					placeInArray(mi, bram, impl);
					//mi.place(impl.getAnchor().getSite());

					this.instances.add(mi);
				}
			}

			System.out.println("max column: "+maxTileColumn);

			t.stop().start("Stitch design");

			// Create clk and rst
			String bufgInstName = "bufgce_inst";
			SLRCrosserGenerator.createBUFGCE(design, CLK, CLK + "in", CLK + "out", bufgInstName);
			SLRCrosserGenerator.placeBUFGCE(design, device.getSite("BUFGCE_X0Y8"), bufgInstName);
			EDIFNet clk = top.getNet(CLK);
			top.createPort(RST, EDIFDirection.INPUT, 1);

			// Connect pre-implemented modules together
			String busRange = "["+(PICOBLAZE_BUS_WIDTH-1)+":0]";
			for(int x=0; x < bramColumns; x++){
				top.createPort(TOP_INPUT_PREFIX + x + busRange, EDIFDirection.INPUT, PICOBLAZE_BUS_WIDTH);
				top.createPort(TOP_OUTPUT_PREFIX + x + busRange, EDIFDirection.OUTPUT, PICOBLAZE_BUS_WIDTH);
				// we will skip top and bottom clock region rows to avoid laguna tiles and U-turn routing
				for(int y=BRAMS_IN_CLOCK_REGION_HEIGHT; y < bramRows-BRAMS_IN_CLOCK_REGION_HEIGHT; y++){
					T curr = getPicoblazeInst(design, x, y, instances);
					if(curr==null) continue;

					clk.createPortInst(CLK, curr.getCellInst());
					curr.connect(RST, RST);

					for(int i=0; i < PICOBLAZE_BUS_WIDTH; i++){
						for(int j=0; j < CONN_ARRAY.length; j++){
							T other = getPicoblazeInst(design, x, y+CONN_ARRAY[j], instances);
							if(other == null){
								curr.connect(PICOBLAZE_INPUTS [j], TOP_INPUT_PREFIX  + x, i);
								if(y == bramRows-BRAMS_IN_CLOCK_REGION_HEIGHT-1 && j==3){
									curr.connect(PICOBLAZE_OUTPUTS[j], TOP_OUTPUT_PREFIX + x, i);
								}
							}else{
								curr.connect(PICOBLAZE_INPUTS [j], other, PICOBLAZE_OUTPUTS[CONN_ARRAY.length-j-1], i);
							}
						}
					}
				}
			}
			return design;
		}

		protected abstract void placeInArray(T mi, Site bram, Module impl);
		protected abstract BlockPlacer2<?, ?, ?> createPlacer(Design design);

		public abstract void lowerToModules(Design design);
	}
	
	/**
	 * Part of an example tutorial of how to build an array of picoblaze modules. 
	 * @param args
	 */
	public static void main(String[] args) {
		OptionParser optionParser = new OptionParser();
		ArgumentAcceptingOptionSpec<String> dirOption = optionParser.accepts("dir", "Module Impls input dir").withRequiredArg().required();
		ArgumentAcceptingOptionSpec<String> partOption = optionParser.accepts("part", "Part to use").withRequiredArg().required();
		ArgumentAcceptingOptionSpec<String> outOption = optionParser.accepts("out", "Output DCP Filename").withRequiredArg().required();
		OptionSpec<?> handPlacerOption = optionParser.accepts("no_hand_placer", "Disable Hand Placer");
		OptionSpec<?> implsOption = optionParser.accepts("impls", "Use Impls instead of Modules");


		OptionSet options;
		try {
			options = optionParser.parse(args);
		} catch (RuntimeException e) {
			try {
				optionParser.printHelpOn(System.out);
			} catch (IOException ioException) {
				throw new UncheckedIOException(ioException);
			}
			throw e;
		}

		String srcDirName = options.valueOf(dirOption);
		File srcDir = new File(srcDirName);
		if (!srcDir.isDirectory()) {
						throw new RuntimeException("ERROR: Couldn't read directory: " + srcDir);
		}
		String part = options.valueOf(partOption);
		String outName = options.valueOf(outOption);
		boolean noHandPlacer = options.has(handPlacerOption);
		boolean useImpls = options.has(implsOption);
		CodePerfTracker t = new CodePerfTracker("PicoBlaze Array", true).start("Creating design");


		PicoBlazeArrayCreator<?> creator;
		if (useImpls) {
			creator = new PicoBlazeArrayCreator<ModuleImplsInstance>() {

				private BlockPlacer2ImplsDebug placer;

				@Override
				protected ModuleImplsInstance createInstance(Design design, String name, Module impl, ModuleImpls impls) {
					return DesignTools.createModuleImplsInstance(design, name, impls);
				}

				@Override
				protected void placeInArray(ModuleImplsInstance mi, Site bram, Module impl) {
					mi.place(new ModulePlacement(impl.getImplementationIndex(), bram));
				}

				@Override
				protected BlockPlacer2<ModuleImplsInstance, ?, ?> createPlacer(Design design) {
					placer = new BlockPlacer2ImplsDebug(design, getInstances(), getMaxTileColumn());
					return placer;
				}

				@Override
				public void lowerToModules(Design design) {
					DesignTools.createModuleInstsFromModuleImplsInsts(design, getInstances(), placer.getPaths());
				}
			};
		} else {

			creator = new PicoBlazeArrayCreator<ModuleInst>() {
				@Override
				protected ModuleInst createInstance(Design design, String name, Module impl, ModuleImpls impls) {
					return design.createModuleInst(name, impl);
				}

				@Override
				protected void placeInArray(ModuleInst mi, Site bram, Module impl) {
					mi.place(bram);
				}

				@Override
				protected BlockPlacer2<?, ?, ?> createPlacer(Design design) {
					return new BlockPlacer2ModuleDebug(design, getMaxTileColumn());
				}

				@Override
				public void lowerToModules(Design design) {
					//Nothing to do
				}
			};
		}

		Design design = creator.createDesign(srcDir, part, t);


		//DotEdifDumper.dump(srcDir.toPath().resolve("edif.dot"), design);
		//DotPhysicalDumper.dump(srcDir.toPath().resolve("physical.dot"), design);


		System.out.println("wait for return key press...");
		MessageGenerator.waitOnAnyKey();

		t.stop().start("BlockPlacer");
		creator.createPlacer(design).placeDesign(true);

		creator.lowerToModules(design);

		if (!noHandPlacer) {
			t.stop().start("Hand Placer");
			System.out.println("start hand placer");
			HandPlacer.openDesign(design);
			System.out.println("finish hand placer");
		}

		t.stop().start("Write DCP");

		design.setAutoIOBuffers(false);
		design.addXDCConstraint("create_clock -name " + CLK + " -period 2.850 [get_nets " + CLK + "]");
		design.writeCheckpoint(outName, CodePerfTracker.SILENT);
		t.stop().printSummary();
	}

}
