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
package com.xilinx.rapidwright.verilogModules;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.ipi.BlockCreator;
import com.xilinx.rapidwright.ipi.IPCore;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.Job;
import com.xilinx.rapidwright.util.JobQueue;


/**
 * Manages pre-implemented block creation.
 * 
 * Created on: Aug 14, 2015
 */
public class BlockCreatorVerilog {

	public static final String SYNTH_RUN_SCRIPT_NAME = "launch_synth_run.tcl";


	private static void copyVerilogToCache(Path verilogFile, IPCore core) {
		try {
			Files.createDirectories(verilogFile.getParent());
			Files.copy(core.getVerilogImplementation(), verilogFile);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void verifyVerilogIdentical(Path verilogFile, IPCore core) {
		try {
			Iterator<String> cacheLines = Files.lines(verilogFile).iterator();
			Iterator<String> projectLines = Files.lines(core.getVerilogImplementation()).iterator();

			while (cacheLines.hasNext() || projectLines.hasNext()) {
				if (cacheLines.hasNext() != projectLines.hasNext()) {
					throw new RuntimeException("Cached Verilog file at\n"+verilogFile+"\nhas different line count than project file at\n"+core.getVerilogImplementation());
				}
				String cacheLine = cacheLines.next();
				String projectLine = projectLines.next();

				if (!cacheLine.equals(projectLine)) {
					throw new RuntimeException("Cached Verilog file at\n"+verilogFile+"\nhas differing line than project file at\n"+core.getVerilogImplementation()+"\n"+cacheLine+" vs "+projectLine);
				}
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void synthBlocks(Map<String, IPCore> ipNames, String cacheDir, String partName) {
		JobQueue jobs = new JobQueue();
		Map<Long,String> jobLocations = new HashMap<>();

		Map<String, List<IPCore>> byHash = ipNames.values().stream().collect(Collectors.groupingBy(IPCore::getHash, Collectors.toList()));

		for(Entry<String, List<IPCore>> e : byHash.entrySet()) {
			String cacheID = e.getKey();
			IPCore core = e.getValue().get(0); //Just get any one, they are identical

			Path cachedIPDir = Paths.get(cacheDir).resolve(core.getHash());
			Path verilogFile = cachedIPDir.resolve("source.v");
			if (!Files.exists(verilogFile)) {
				copyVerilogToCache(verilogFile, core);
			}

			Path optDcp = cachedIPDir.resolve("design_opt.dcp");
			if (!Files.exists(optDcp)) {
				Job job = createSynthRun(cachedIPDir, core.getName(), partName, e.getValue().size());
				jobs.addJob(job);
			}
		}
		/*if (true) {
			throw new RuntimeException("todo, "+devName);
		}*/
		boolean success = jobs.runAllToCompletion();
		if (!success) {
			throw new RuntimeException("Failed to complete jobs");
		}
	}

	public static Job createSynthRun(Path cacheDir, String topName, String partName, long instanceCount){
		Path scriptName = cacheDir.resolve(SYNTH_RUN_SCRIPT_NAME);

		Job j = JobQueue.createJob();
		j.setRunDir(cacheDir.toString());
		j.setCommand(FileTools.getVivadoPath() + " -mode batch -source " + scriptName);

		try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(scriptName))) {
			BlockCreator.printCheckForRapidWrightTcl(pw);
			pw.println("read_verilog source.v");
			//If we print the top name directly, $ get interpreted...
			pw.println("set top {"+topName+"}");
			pw.println("synth_design -mode out_of_context -top $top -part "+partName);
			pw.println("write_checkpoint design_synth.dcp");
			/*pw.println("opt_design");
			pw.println("write_checkpoint design_opt.dcp");
			pw.println("report_utilization -packthru -file design_utilization.report");
			pw.println("write_edif design_routed.edf");*/
			pw.println("compile_openened_block_dcp design.dcp "+instanceCount);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return j;
	}

}
