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

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.util.FileTools;


/**
 * Parses an XDC file for package constraints only.  Does not
 * perform full XDC parsing.
 * 
 * Created on: Jul 27, 2015
 */
public class XDCParser {

	private static boolean expect(String expected, String found, int lineNum, String line){
		if(!expected.equals(found)){
			throw new RuntimeException("\nERROR: While parsing line:\n   '" +
				line + "' (line number " + lineNum + ")\n" + "   Expected: '" +
					expected + "'\n      Found: '" + found + "'\nStack Trace:");
		}
		return true;
	}


	private static String getElement(String elementType, int lineNum, String line, String[] parts, int startPartIdx) {
		expect("[get_"+elementType, parts[startPartIdx], lineNum, line);
		String key = parts[startPartIdx+1].substring(0, parts[startPartIdx+1].lastIndexOf(']'));
		key = key.replace("}", "");
		key = key.replace("{", "");
		return key;
	}

	private static PackagePinConstraint getPinConstraint(int lineNum, String line, String[] parts, int startPartIdx, XDCConstraints constraints) {
		String key = getElement("ports", lineNum, line, parts, startPartIdx);
		PackagePinConstraint pkgPin = constraints.getPinConstraints().get(key);
		if(pkgPin == null){
			pkgPin = new PackagePinConstraint();
			constraints.getPinConstraints().put(key, pkgPin);
		}
		return pkgPin;
	}

	/**
	 * Very rudimentary parsing to extract IO placements and IO standards.  This
	 * does not support many Tcl constructs.
	 * @param fileName Name of the XDC file to parse
	 * @param dev The device associated with the design.
	 * @return A map of port names to package pin information.
	 */
	public static XDCConstraints parseXDCNew(String fileName, Device dev){
		XDCConstraints constraints = new XDCConstraints();
		int lineNum = 1;
		for(String line : FileTools.getLinesFromTextFile(fileName)){
			if(line.trim().startsWith("#")) continue;
			if(line.contains("set_property") && line.contains("PACKAGE_PIN")){
				parseLocation(dev, constraints, lineNum, line);
			}else if(line.contains("set_property") && line.contains("IOSTANDARD")) {
				parseIoStandard(constraints, lineNum, line);
			} else if (line.contains("set_property")) {
				parseSetProperty(constraints, lineNum, line);
			} else if (line.contains("create_clock")) {
				parseClock(constraints, lineNum, line);
			} else {
				constraints.getUnsupportedConstraints().add(line);
			}
			lineNum++;
		}

		
		return constraints;
	}

	private static void parseClock(XDCConstraints constraints, int lineNum, String line) {
		String[] parts = line.split("\\s+");
		expect("create_clock", parts[0], lineNum, line);
		String port = null;
		String period = null;
		String clockName = null;
		for (int i=1; i<parts.length-1; i++) {
			if (parts[i].startsWith("-")) {
				if (parts[i].equals("-period")) {
					period = parts[++i];
				} else if (parts[i].equals("-name")) {
					clockName = parts[++i];

				} else if (parts[i].equals("-waveform")) {
					//Just skip the waveform specification
					while (!parts[i].endsWith("}")) {
						++i;
					}
				} else {
					expect("-name | -period | -waveform", parts[i], lineNum, line);
				}
			} else {
				port = getElement("ports",lineNum, line, parts, i);
				++i;

				if (i+1 != parts.length) {
					throw new RuntimeException("Extra elements after port name in: "+line);
				}
			}
		}
		constraints.getClockConstraints().put(port, new ClockConstraint(clockName, period));

	}

	private static void parseSetProperty(XDCConstraints constraints, int lineNum, String line) {
		String[] parts = line.split("\\s+");
		expect("set_property", parts[0], lineNum, line);
		String propName = parts[1];
		String value = parts[2];
		String cell = getElement("cells", lineNum, line, parts, 3);
		constraints.getCellProperties().computeIfAbsent(cell, x-> new HashMap<>()).put(propName, value);
	}

	private static void parseIoStandard(XDCConstraints constraints, int lineNum, String line) {
		String[] parts = line.split("\\s+");
		expect("set_property", parts[0], lineNum, line);
		expect("IOSTANDARD", parts[1], lineNum, line);
		String ioStandard = parts[2];
		PackagePinConstraint pkgPin = getPinConstraint(lineNum, line, parts, 3, constraints);
		pkgPin.setIOStandard(ioStandard);
	}

	private static void parseLocation(Device dev, XDCConstraints constraints, int lineNum, String line) {
		String[] parts = line.split("\\s+");
		expect("set_property", parts[0], lineNum, line);
		expect("PACKAGE_PIN", parts[1], lineNum, line);
		String pinLoc = parts[2];
		if(!dev.getActivePackage().getPackagePinMap().containsKey(pinLoc)){
			expect("<VALID_PKG_PIN>", pinLoc, lineNum, line);
		}
		PackagePinConstraint pkgPin = getPinConstraint(lineNum, line, parts, 3, constraints);
		pkgPin.setName(pinLoc);
	}

	public static Map<String,PackagePinConstraint> parseXDC(String fileName, Device dev) {
		return parseXDCNew(fileName, dev).getPinConstraints();
	}

	public static void writeXDC(List<String> constraints, OutputStream out){
		if(constraints == null) return;
		try {
			for(String s : constraints){
				out.write(s.getBytes());
				out.write('\n');
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
