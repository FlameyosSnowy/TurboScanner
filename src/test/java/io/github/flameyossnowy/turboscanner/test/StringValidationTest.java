package io.github.flameyossnowy.turboscanner.test;

import io.github.flameyossnowy.turboscanner.ByteScanner;
import io.github.flameyossnowy.turboscanner.ScanResult;
import io.github.flameyossnowy.turboscanner.VectorByteScanner;
import io.github.flameyossnowy.turboscanner.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringValidationTest {

    private final ByteScanner scanner = new VectorByteScanner();

    @Test
    void validString() {
        byte[] json = "{\"a\":\"hello\"}".getBytes();

        ScanResult r = ScanResult.create(json.length);
        int result = scanner.scan(json, 0, json.length, r);

        assertDoesNotThrow(() ->
            Validators.validateStrings(json, r)
        );
    }

    @Test
    void unterminatedString() {
        byte[] json = "{\"a\":\"hello}".getBytes();
        ScanResult r = ScanResult.create(json.length);

        int result = scanner.scan(json, 0, json.length, r);
        System.out.println(result);

        assertThrows(JsonException.class, () ->
            Validators.validateStrings(json, r)
        );
    }

    @Test
    void unescapedControl() {
        byte[] json = "{\"a\":\"hello\n\"}".getBytes();

        ScanResult r = ScanResult.create(json.length);
        int result = scanner.scan(json, 0, json.length, r);

        assertThrows(JsonException.class, () ->
            Validators.validateStrings(json, r)
        );
    }
}
