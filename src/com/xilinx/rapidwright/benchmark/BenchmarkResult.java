package com.xilinx.rapidwright.benchmark;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.tests.CodePerfTracker;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class BenchmarkResult {

    private static final String MIN_PERIOD_NOT_MET = "minPeriodNotMet";
    private static final String MIN_PERIOD_MET = "minPeriodMet";
    private static final String RUNTIMES = "runtimes";
    private static final String EXCEPTION = "exception";

    private static final String NAME = "name";
    private static final String RUNTIME = "runtime";
    private static final String MEMORY = "memory";

    public BenchmarkResult(SomeRun run) {
        this.run = run;
    }

    public static class RuntimeLog {
        public final String name;
        public final long runtime;
        public final long memory;

        public RuntimeLog(String name, long runtime, long memory) {
            this.name = name;
            this.runtime = runtime;
            this.memory = memory;
        }

        public static RuntimeLog fromJson(JSONObject o) {
            return new RuntimeLog(
                    o.getString(NAME),
                    o.getLong(RUNTIME),
                    o.getLong(MEMORY)
            );
        }

        private JSONObject toJson() {
            JSONObject o = new JSONObject();
            o.put(NAME, name);
            o.put(RUNTIME, runtime);
            o.put(MEMORY, memory);
            return o;
        }

        public static JSONArray toJson(List<RuntimeLog> runtimes) {
            JSONArray r = new JSONArray();
            for (RuntimeLog l : runtimes) {
                r.put(l.toJson());
            }
            return r;
        }

        public static ArrayList<RuntimeLog> fromJson(JSONArray runtimes) {
            if (runtimes == null) {
                return null;
            }
            final ArrayList<RuntimeLog> res = new ArrayList<>(runtimes.length());
            for( Object o : runtimes) {
                if (! (o instanceof JSONObject)) {
                    throw new RuntimeException("not a jsonobject: "+o);
                }
                res.add(RuntimeLog.fromJson((JSONObject) o));
            }
            return res;
        }

        public static List<RuntimeLog> fromCodePerfTracker(CodePerfTracker t) {

            List<RuntimeLog> result = new ArrayList<>(t.getSegmentCount());
            for (int i = 0; i < t.getSegmentCount(); i++) {
                result.add(new RuntimeLog(
                        t.getSegmentName(i),
                        t.getRuntime(i),
                        t.getMemUsage(i)
                ));

            }
            return result;
        }


    }
    public Path jsonFile;
    public String exception;
    public List<RuntimeLog> runtimes;
    public double minPeriodMet = Double.NaN;
    public double minPeriodNotMet = Double.NaN;

    public final SomeRun run;

    EvalData evalData = null;
    public EvalData getEvalData(Path workDirRoot) {
        if (evalData == null) {
            evalData = EvalData.fromRunDir(jsonFile.getParent(), run, workDirRoot, this);
        }
        return evalData;
    }

    public void toJson(Path json) {
        JSONObject o = toJson();
        try (BufferedWriter wr = Files.newBufferedWriter(json)) {
            o.write(wr);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public static BenchmarkResult fromJson(SomeRun run, Path json) {
        if (!Files.exists(json)) {

            try {

                final List<Path> crashLog;
                final Path parent = json.getParent();
                if (Files.exists(parent)) {
                    crashLog = Files.list(parent).filter(f -> f.getFileName().toString().matches("hs_err_pid.*\\.log")).collect(Collectors.toList());
                } else {
                    crashLog = Collections.emptyList();
                }
                BenchmarkResult r = new BenchmarkResult(run);
                if (!crashLog.isEmpty()) {
                    r.exception = "no result present, but Java crash log: " + crashLog;
                } else {
                    r.exception = "not yet run";
                }
                return r;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        try (BufferedReader reader = Files.newBufferedReader(json)) {
            JSONObject rootObject = new JSONObject(new JSONTokener(reader));
            final BenchmarkResult benchmarkResult = fromJson(run, rootObject);

            benchmarkResult.jsonFile = json;
            return benchmarkResult;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BenchmarkResult fromJson(SomeRun run, JSONObject rootObject) {
        final BenchmarkResult result = new BenchmarkResult(run);
        result.exception = rootObject.optString(EXCEPTION, null);
        result.minPeriodMet = rootObject.optDouble(MIN_PERIOD_MET, Double.NaN);
        result.minPeriodNotMet = rootObject.optDouble(MIN_PERIOD_NOT_MET, Double.NaN);
        result.runtimes = RuntimeLog.fromJson(rootObject.optJSONArray(RUNTIMES));
        return result;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put(EXCEPTION, exception);
        if (!Double.isNaN(minPeriodMet)) {
            o.put(MIN_PERIOD_MET, minPeriodMet);
        }
        if (!Double.isNaN(minPeriodNotMet)) {
            o.put(MIN_PERIOD_NOT_MET, minPeriodNotMet);
        }
        if (runtimes != null) {
            o.put(RUNTIMES, RuntimeLog.toJson(runtimes));
        }
        return o;
    }

    @Override
    public String toString() {
        if (exception != null) {
            String witnoutStacktrace = Arrays.stream(exception.split("\n")).filter(l->!l.trim().startsWith("at ")).collect(Collectors.joining("\n"));
            return "error: "+witnoutStacktrace;
        }

        return "min period "+minPeriodNotMet+" - "+minPeriodMet+" ("+(1000/minPeriodNotMet)+" - "+(1000/minPeriodMet)+")";
    }
}
