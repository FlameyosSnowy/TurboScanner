package io.github.flameyossnowy.turboscanner.bench;

import io.github.flameyossnowy.turboscanner.ScanResult;
import io.github.flameyossnowy.turboscanner.*;
import org.openjdk.jmh.annotations.*;

public class FullValidationBench {

    @Benchmark
    public void validateAscii(JsonScanBenchState s) {
        ScanResult r = ScanResult.create(s.asciiJson.length);
        int result = s.scanner.scan(s.asciiJson, 0, s.asciiJson.length, r);
        Validators.validateStructure(s.asciiJson, r);
        Validators.validateStrings(s.asciiJson, r);
    }

    @Benchmark
    public void validateUtf8(JsonScanBenchState s) {
        ScanResult r = ScanResult.create(s.utf8Json.length);
        int result = s.scanner.scan(s.utf8Json, 0, s.utf8Json.length, r);
        Validators.validateStructure(s.utf8Json, r);
        Validators.validateStrings(s.utf8Json, r);
    }

    @Benchmark
    public void validateDeep(JsonScanBenchState s) {
        ScanResult r = ScanResult.create(s.deepJson.length);
        int result = s.scanner.scan(s.deepJson, 0, s.deepJson.length, r);
        Validators.validateStructure(s.deepJson, r);
        Validators.validateStrings(s.deepJson, r);
    }
}
