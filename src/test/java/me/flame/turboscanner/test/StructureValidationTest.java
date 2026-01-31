package me.flame.turboscanner.test;

import me.flame.turboscanner.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureValidationTest {

    private final ByteScanner scanner = new VectorByteScanner();

    @Test
    void validStructure() {
        byte[] json = "{\"a\":[1,2,3]}".getBytes();
        ScanResult r = ScanResult.create(json.length);
        int result = scanner.scan(json, 0, json.length, r);

        assertDoesNotThrow(() ->
            Validators.validateStructure(json, r)
        );
    }

    @Test
    void unbalancedClosing() {
        byte[] json = "]}".getBytes();
        ScanResult r = ScanResult.create(json.length);
        int result = scanner.scan(json, 0, json.length, r);

        assertThrows(JsonException.class, () ->
            Validators.validateStructure(json, r)
        );
    }

    @Test
    void missingClosing() {
        byte[] json = "{\"a\":[".getBytes();
        ScanResult r = ScanResult.create(json.length);
        int result = scanner.scan(json, 0, json.length, r);

        assertThrows(JsonException.class, () ->
            Validators.validateStructure(json, r)
        );
    }
}
