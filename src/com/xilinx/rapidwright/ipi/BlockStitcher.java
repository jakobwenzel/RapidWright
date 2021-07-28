/* 
 * Copyright (c) 2017 Xilinx, Inc. 
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
/**
 * 
 */
package com.xilinx.rapidwright.ipi;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.xilinx.rapidwright.design.blocks.ImplGuide;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.util.FileTools;

/**
 * Main flow for processing and stitching pre-implemented modules together
 * from IP Integrator-based designs.
 * 
 * Created on: Jun 19, 2015
 */
public class BlockStitcher extends AbstractBlockStitcher {

	private final Path ipNames;
	private HashSet<String> uniqueModInstNames = new HashSet<String>();

	public BlockStitcher(Path cacheDir, Path edifFile, Path ipNames) {
		super(cacheDir, edifFile);
		this.ipNames = ipNames;
	}


	public String getModuleInstName(EDIFNetlist module, EDIFNetlist netlist, HashMap<String,String> modInstNameMap){
		String fullInstName = modInstNameMap.get(module.getTopCell().getName());

		boolean first = true;
		int i=0;
		while(uniqueModInstNames.contains(fullInstName)){
			i++;
			if(first) {
				fullInstName = fullInstName + "_" + i;
				first = false;
			}else{
				fullInstName = fullInstName.substring(0, fullInstName.lastIndexOf('_')+1) + i;
			}
		}
		uniqueModInstNames.add(fullInstName);
		return fullInstName;
	}


	@Override
	protected void implementBlocks(Map<String, IPCore> ipNames, ImplGuide implHelper, Device device) {
		BlockCreator.implementBlocks(ipNames, cacheDir.toFile().getAbsolutePath(), implHelper, device);
	}

	@Override
	public Map<String,IPCore> getPartAndIPNames(EDIFNetlist topEdifNetlist){
		ArrayList<String> lines = FileTools.getLinesFromTextFile(ipNames.toString());
		Map<String,IPCore> names = new HashMap<>();
		boolean first = true;
		for(String line : lines){
			if(first){
				first = false;
				partName = line.trim();
				continue;
			}
			String[] parts = line.split(" ");
			names.put(parts[0], new IPCore(parts[0], parts[2], parts[3].substring(1), null));
		}
		return names;
	}


	public static void run(Path runDirectory, Path edif, Path ipNames) {
		new BlockStitcher(runDirectory, edif, ipNames).stitch();
	}

	public static void
	main(String[] args) {
		if(args.length != 3){
			System.out.println("USAGE: <directory to runs> <top_level_edif_file> <file_of_ip_names>");
			return;
		}
		run(Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]));

	}
}

