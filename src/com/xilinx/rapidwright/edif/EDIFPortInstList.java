/* 
 * Copyright (c) 2022 Xilinx, Inc. 
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
 
package com.xilinx.rapidwright.edif;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Customized ArrayList<EDIFPortInst> for the {@link EDIFNet} and {@link EDIFCellInst} classes. 
 * Maintains a sorted list to allow for a O(log n) retrieval lookup by name.  Does not allow 
 * duplicate entries. 
 */
public class EDIFPortInstList implements Set<EDIFPortInst> {

    private static final long serialVersionUID = 7446248479402248969L;
    
    public static final EDIFPortInstList EMPTY = new EDIFPortInstList();

    private final List<EDIFPortInst> container = new ArrayList<>();

    @Override
    public int size() {
        return container.size();
    }

    @Override
    public boolean isEmpty() {
        return container.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return container.contains(o);
    }

    @NotNull
    @Override
    public Iterator<EDIFPortInst> iterator() {
        return container.iterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return container.toArray();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        return container.toArray(a);
    }

    @Override
    public boolean add(EDIFPortInst e) {
        int insertionPoint = binarySearch(e.getCellInst(), e.getPort().getName(), e.getIndex());
        // Do not allow duplicates
        if(insertionPoint >= 0) {
            return false;
        }
        container.add(~insertionPoint, e);
        return true;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return container.containsAll(c);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends EDIFPortInst> c) {
        boolean changed = false;
        for (EDIFPortInst edifPortInst : c) {
            changed |= add(edifPortInst);
        }
        return changed;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return container.retainAll(c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        boolean changed = false;
        for (Object o : c) {
            changed |= remove(o);
        }
        return changed;
    }

    @Override
    public void clear() {
        container.clear();
    }

    public EDIFPortInst get(EDIFCellInst i, String portName, int portIndex) {
        int index = binarySearch(i, portName, portIndex);
        if(index < 0) return null;
        return container.get(index);
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof EDIFPortInst)) {
            return false;
        }
        EDIFPortInst e = (EDIFPortInst) o;
        return remove(e.getCellInst(), e.getPort().getName(), e.getIndex()) != null;
    }

    public EDIFPortInst remove(EDIFCellInst inst, String portName, int portIndex) {
        int index = binarySearch(inst, portName, portIndex);
        if(index < 0) return null;
        return container.remove(index);
    }
    
    private int binarySearch(EDIFCellInst inst, String portName, int index) {
        String instName = inst == null ? null : inst.getName();
        int left = 0;
        int right = size()-1;
        while(left <= right) {
            int pivot = (left + right) >>> 1;
            int result = compare(container.get(pivot), instName, portName, index);
            if(result < 0) {
                left = pivot + 1;
            } else if (result > 0) {
                right = pivot - 1;
            } else {
                return pivot;
            }
        }
        return ~left;
    }
    
    /**
     * Performs a 'compareTo' operation without having to create disposable Strings or EDIFPortInst 
     * objects.  Performs the same operation as 'left.getFullName().compareTo(right.getFullName())'
     *  where right is the EDIFPortInst represented by rightInstName and rightPortInstName.  
     * @param left This is the existing EDIFPortInst within the lists that is being compared
     * @param rightInstName This is the cell instance name {@link EDIFCellInst#getName()} of the 
     * considered port instance to compare left against.  
     * @param rightPortName This is the port name {@link EDIFPort#getName()} of the
     * considered port instance's port to compare left against.
     * @param rightPortName This is the port instance index {@link EDIFPortInst#getIndex()} of the
     * considered port instance to compare left against.
     * @return 0 if the left and corresponding right Strings are equal.  A number less than 0 if
     * left is before right, or a number greater than 0 if left is after right.
     */
    private int compare(EDIFPortInst left, String rightInstName, String rightPortName, int rightIndex) {
        String leftInstName = left.getCellInst() != null ? left.getCellInst().getName() : null;
        int compare1 = Comparator.nullsFirst(Comparator.<String>naturalOrder()).compare(leftInstName, rightInstName);
        if (compare1 != 0) {
            return compare1;
        }
        int compare2 = left.getPort().getName().compareTo(rightPortName);
        if (compare2 != 0) {
            return compare2;
        }
        return Integer.compare(left.getIndex(), rightIndex);
    }

    @Deprecated
    public EDIFPortInst get(EDIFCellInst edifCellInst, String name) {
        int index = name.endsWith("]") ? EDIFTools.getPortIndexFromName(name) : -1;
        return get(edifCellInst, EDIFTools.getRootBusName(name), index);
    }
}
