package com.xilinx.rapidwright.placer.blockplacer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.device.Tile;

public class PlacementCollection<PlacementT> {
    private static class PlacementCollection1D<T>{


        public final List<T> items;
        public final int[] keys;

        public final int[] minIdx;
        public final int[] maxIdx;
        public PlacementCollection1D(List<T> items, int[] keys) {
            this.items = items;
            this.keys = keys;

            int biggestKey = keys[keys.length-1];

            minIdx = new int[biggestKey+2];
            maxIdx = new int[biggestKey+2];

            for (int key = 0; key < minIdx.length; key++) {
                minIdx[key] = getMinIdxOld(key);
                maxIdx[key] = getMaxIdxOld(key);
            }
        }

        private int getMinIdxOld(int key) {
            int res = Arrays.binarySearch(keys, key);
            if (res>=0) {
                //When multiple entries match, it is undefined which one is returned. Make sure we return the first one
            /*while (res > 0 && keyArr[res-1] == key) {
                res--;
            }*/
                return res;
            }
            //Extract the insertion point
            return -(res+1);
        }

        private int getMaxIdxOld(int key) {
            int res = Arrays.binarySearch(keys, key);
            if (res>=0) {
                //When multiple entries match, it is undefined which one is returned. Make sure we return the last one
            /*while (res < (keyArr.length-1) && keyArr[res+1] == key) {
                res++;
            }*/
                return res;
            }
            //Extract the insertion point
            int insertionPoint = -(res+1);
            //Exclude the insertion point
            return insertionPoint -1;
        }

        private int fromArr(int key, int[] arr) {
            if (key < 0) {
                return 0;
            }
            if (key >= arr.length) {
                return arr[arr.length-1];
            }
            return arr[key];
        }

        public int getMaxIdx(int key) {
            final int i = fromArr(key, maxIdx);
            /*final int old = getMaxIdxOld(key);
            if (old != i) {
                throw new RuntimeException("wrong max");
            }*/
            return i;
        }
        public int getMinIdx(int key) {
            final int i = fromArr(key, minIdx);
            /*final int old = getMinIdxOld(key);
            if (old != i) {
                throw new RuntimeException("wrong min");
            }*/
            return i;
        }

        public static <T,U>Collector<T,?,PlacementCollection1D<U>> collector(ToIntFunction<T> keyExtractor,  Collector<T,?,U> downstreamCollector) {
            return Collectors.collectingAndThen(Collectors.groupingBy(keyExtractor::applyAsInt, downstreamCollector), (Map<Integer, U> byKey) -> {
                int[] keys = byKey.keySet().stream().sorted().mapToInt(x -> x).toArray();
                List<U> items = Arrays.stream(keys).mapToObj(byKey::get).collect(Collectors.toList());
                return new PlacementCollection1D<>(items, keys);
            });
        }

        public T get(int idx) {
            return items.get(idx);
        }

        public T getByKeyOld(int key) {
            final int idx = Arrays.binarySearch(keys,  key);


            if (idx<0) {
                return null;
            }
            return items.get(idx);
        }

        public T getByKeyNew(int key) {

            final int max = getMaxIdx(key);
            final int min = getMinIdx(key);
            if (max != min) {
                return null;
            }
            return items.get(min);
        }

        public T getByKey(int key) {
            T newT = getByKeyNew(key);
            /*T old = getByKeyOld(key);
            if (old != newT) {
                throw new RuntimeException("wrong get by key");
            }*/
            return newT;
        }
    }
    private final PlacementCollection1D<PlacementCollection1D<List<PlacementT>>> collection;
    private final Function<PlacementT, Tile> getPlacementTile;

    private PlacementCollection(Function<PlacementT, Tile> getPlacementTile, PlacementCollection1D<PlacementCollection1D<List<PlacementT>>> collection) {
        this.getPlacementTile = getPlacementTile;

        this.collection = collection;
    }

    public static <PlacementT> Collector<PlacementT, ?, PlacementCollection<PlacementT>> collector(Function<PlacementT, Tile> getPlacementTile) {
        Collector<PlacementT, ?, PlacementCollection1D<PlacementCollection1D<List<PlacementT>>>> createColl =
                PlacementCollection1D.collector(
                        p -> getPlacementTile.apply(p).getColumn(),
                        PlacementCollection1D.collector(
                                p -> getPlacementTile.apply(p).getRow(),
                                Collectors.toList()
                        )
                );
        return Collectors.collectingAndThen(createColl, c -> new PlacementCollection<>(getPlacementTile, c));
    }


    public List<PlacementT> getByRangeAround(int rangeLimit, Tile center) {
        List<PlacementT> result = new ArrayList<>();



        final int maxColumn = collection.getMaxIdx(center.getColumn()+rangeLimit);
        for (int col = collection.getMinIdx(center.getColumn() - rangeLimit); col <= maxColumn; col++) {
            final PlacementCollection1D<List<PlacementT>> currentCol = collection.get(col);

            final int maxRow = currentCol.getMaxIdx(center.getRow()+rangeLimit);
            for (int row = currentCol.getMinIdx(center.getRow()-rangeLimit); row <= maxRow; row++) {
                result.addAll(currentCol.items.get(row));
            }
            /*for (PlacementT placementT : flatEntries.get(col)) {
                final int row = getPlacementTile.apply(placementT).getRow();
                if (row >= minRow && row <= maxRow) {
                    result.add(placementT);
                }
            }*/
        }

        return result;
    }

    public boolean contains(PlacementT placement) {
        final Tile tile = getPlacementTile.apply(placement);

        final PlacementCollection1D<List<PlacementT>> column = collection.getByKey(tile.getColumn());
        if (column == null) {
            return false;
        }
        List<PlacementT> row = column.getByKey(tile.getRow());
        if (row == null) {
            return false;
        }
        return row.contains(placement);
    }
}
