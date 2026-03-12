package io.github.flameyossnowy.turboscanner.bench;

import io.github.flameyossnowy.turboscanner.*;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class NaiveScalarScanBench {

    private NaiveScalarByteScanner scanner;

    @Setup(Level.Trial)
    public void setup() {
        scanner = new NaiveScalarByteScanner();
    }

    @Benchmark
    public int scanAscii(JsonScanBenchState state) {
        ScalarScanResult r = new ScalarScanResult(state.asciiJson.length);
        return scanner.scan(state.asciiJson, 0, state.asciiJson.length, r);
    }

    @Benchmark
    public int scanUtf8(JsonScanBenchState state) {
        ScalarScanResult r = new ScalarScanResult(state.utf8Json.length);
        return scanner.scan(state.utf8Json, 0, state.utf8Json.length, r);
    }

    @Benchmark
    public int scanDeep(JsonScanBenchState state) {
        ScalarScanResult r = new ScalarScanResult(state.deepJson.length);
        return scanner.scan(state.deepJson, 0, state.deepJson.length, r);
    }

    @Benchmark
    public int scanStringHeavy(JsonScanBenchState state) {
        ScalarScanResult r = new ScalarScanResult(state.stringHeavyJson.length);
        return scanner.scan(state.stringHeavyJson, 0, state.stringHeavyJson.length, r);
    }
}
