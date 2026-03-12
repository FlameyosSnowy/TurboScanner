package io.github.flameyossnowy.turboscanner.bench;

import io.github.flameyossnowy.turboscanner.ScanResult;
import io.github.flameyossnowy.turboscanner.*;
import org.openjdk.jmh.annotations.*;

public class ScanBench {

    @Benchmark
    public ScanResult scanAscii(JsonScanBenchState s) {
        ScanResult r = ScanResult.create(s.asciiJson.length);
        int result = s.scanner.scan(s.asciiJson, 0, s.asciiJson.length, r);
        return r;
    }

    @Benchmark
    public ScanResult scanUtf8(JsonScanBenchState s) {
        ScanResult r = ScanResult.create(s.utf8Json.length);
        int result = s.scanner.scan(s.utf8Json, 0, s.utf8Json.length, r);
        return r;
    }

    @Benchmark
    public ScanResult scanDeep(JsonScanBenchState s) {
        ScanResult r = ScanResult.create(s.deepJson.length);
        int result = s.scanner.scan(s.deepJson, 0, s.utf8Json.length, r);
        return r;
    }

    @Benchmark
    public ScanResult scanStringHeavy(JsonScanBenchState s) {
        ScanResult r = ScanResult.create(s.stringHeavyJson.length);
        int result = s.scanner.scan(s.stringHeavyJson, 0, s.stringHeavyJson.length, r);
        return r;
    }
}
