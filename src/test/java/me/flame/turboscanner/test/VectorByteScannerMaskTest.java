package me.flame.turboscanner.test;

import me.flame.turboscanner.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class VectorByteScannerMaskTest {

    private final VectorByteScanner simd   = new VectorByteScanner();

    private ScanResult scan(String json) {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);
        return r;
    }

    private boolean structural(ScanResult r, String json, char ch) {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        int idx = json.indexOf(ch);
        if (idx < 0) return false;
        return r.isStructural(idx);
    }

    // ================================================================
    // Quote mask
    // ================================================================

    @Test
    void quoteMask_singleString() {
        String json = "\"hello\"";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        assertTrue(r.isQuote(0), "Opening quote should be set");
        assertTrue(r.isQuote(6), "Closing quote should be set");
        for (int i = 1; i <= 5; i++) assertFalse(r.isQuote(i), "Inside chars should not be quotes");
    }

    @Test
    void quoteMask_multipleStrings() {
        String json = "\"a\",\"b\"";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        assertTrue(r.isQuote(0));
        assertTrue(r.isQuote(2));
        assertTrue(r.isQuote(4));
        assertTrue(r.isQuote(6));
    }

    @Test
    void quoteMask_escapedQuoteDoesNotToggleStringState() {
        // The \" at index 4-5 is inside the string and should not close it
        String json = "\"a\\\"b\"";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        // bytes 1,2,3,4 are inside the string
        assertTrue(r.isInsideString(1));
        assertTrue(r.isInsideString(2));
        assertTrue(r.isInsideString(3));
        assertTrue(r.isInsideString(4));
        // byte 5 is the real closing quote, NOT inside
        assertFalse(r.isInsideString(5));
    }

    // ================================================================
    // Inside-string mask
    // ================================================================

    @Test
    void insideStringMask_correctRange() {
        String json = "{\"key\":\"value\"}";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        // "key" spans indices 2..4, "value" spans indices 8..12
        assertTrue(r.isInsideString(2));
        assertTrue(r.isInsideString(3));
        assertTrue(r.isInsideString(4));
        assertTrue(r.isInsideString(8));
        assertTrue(r.isInsideString(12));

        // structural chars outside strings
        assertFalse(r.isInsideString(0));  // {
        assertFalse(r.isInsideString(6));  // :
        assertFalse(r.isInsideString(14)); // }
    }

    @Test
    void insideStringMask_emptyString() {
        String json = "\"\"";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        assertFalse(r.isInsideString(0)); // opening quote
        assertFalse(r.isInsideString(1)); // closing quote
    }

    // ================================================================
    // Structural mask
    // ================================================================

    @Test
    void structuralMask_allSixChars() {
        String json = "{\"k\":[1,2]}";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        assertTrue(r.isStructural(0),  "{ should be structural");
        assertTrue(r.isStructural(5),  "[ should be structural");
        assertTrue(r.isStructural(7),  ", should be structural");
        assertTrue(r.isStructural(9),  "] should be structural");
        assertTrue(r.isStructural(10), "} should be structural");
    }

    @Test
    void structuralMask_colonIsStructural() {
        String json = "{\"a\":1}";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        int colonIdx = json.indexOf(':');
        assertTrue(r.isStructural(colonIdx), ": should be structural");
    }

    @Test
    void structuralMask_structuralCharsInsideStringAreNotStructural() {
        String json = "\"{},:[]}\"";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        // All chars between the outer quotes are inside a string
        for (int i = 1; i <= 7; i++) {
            assertFalse(r.isStructural(i),
                "Structural char at index " + i + " inside string should not be marked structural");
        }
    }

    // ================================================================
    // Vector boundary / multi-word correctness
    // ================================================================

    @Test
    void masksCorrect_inputExactlyVLEN() {
        // Build input that is exactly 32 bytes (one AVX2 vector)
        String json = "{\"aaaaaaaaaaaaaaaaaaaaaaaaaaaa\":1}";
        // trim/pad to exactly 32 bytes if needed
        byte[] b = new byte[32];
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, b, 0, Math.min(raw.length, 32));
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);
        assertTrue(r.isStructural(0), "{ at 0 should be structural");
    }

    @Test
    void masksCorrect_inputSpansTwoWords() {
        // 130 bytes forces at least 3 words (64+64+2), exercising multi-word writes
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 10; i++) sb.append("\"key").append(i).append("\":").append(i).append(",");
        sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        String json = sb.toString();
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        assertTrue(r.isStructural(0));
        assertTrue(r.isStructural(b.length - 1));
        assertFalse(r.isInsideString(0));
        assertFalse(r.isInsideString(b.length - 1));
    }

    @Test
    void masksCorrect_stringSpansVectorBoundary() {
        // Put an opening quote just before byte 32 so the string body crosses the vector boundary
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append('a'); // 30 non-string bytes
        sb.append("\"crossboundarystring\"");
        String json = sb.toString();
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        int openQuote = 30;
        int closeQuote = openQuote + "crossboundarystring".length() + 1;

        assertTrue(r.isQuote(openQuote),  "Opening quote should be in quote mask");
        assertTrue(r.isQuote(closeQuote), "Closing quote should be in quote mask");
        assertTrue(r.isInsideString(openQuote + 1), "First char inside string should be marked");
        assertTrue(r.isInsideString(closeQuote - 1), "Last char inside string should be marked");
        assertFalse(r.isInsideString(closeQuote), "Closing quote itself should not be inside string");
    }

    // ================================================================
    // Edge cases
    // ================================================================

    @Test
    void emptyInput() {
        byte[] b = new byte[0];
        ScanResult r = ScanResult.create(0);
        int processed = simd.scan(b, 0, 0, r);
        assertEquals(0, processed);
    }

    @Test
    void singleByte_structural() {
        byte[] b = "{".getBytes();
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);
        assertTrue(r.isStructural(0));
    }

    @Test
    void singleByte_quote() {
        byte[] b = "\"".getBytes();
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);
        assertTrue(r.isQuote(0));
    }

    @Test
    void backslashAtVectorBoundarySys() {
        byte[] b = new byte[34];
        for (int i = 0; i < 30; i++) b[i] = 'a';
        b[30] = '"';
        b[31] = '\\';
        b[32] = '"';
        b[33] = '"';
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        // DIAGNOSTIC
        System.out.println("VLEN=" + VectorByteScanner.VLEN);
        System.out.println("insideStringMask[0]=" + Long.toBinaryString(r.getInsideStringMask()[0]));
        System.out.println("isInsideString(31)=" + r.isInsideString(31));
        System.out.println("isInsideString(32)=" + r.isInsideString(32));
        System.out.println("isInsideString(33)=" + r.isInsideString(33));
    }

    @Test
    void noStructuralChars() {
        String json = "12345";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        for (int i = 0; i < b.length; i++) {
            assertFalse(r.isStructural(i), "No structural chars in a plain number");
        }
    }

    @Test
    void backslashAtVectorBoundary() {
        // Place \\ at byte 31 (last byte of first AVX2 vector)
        // The carry must correctly prevent the next " from being treated as a toggle
        byte[] b = new byte[34];
        for (int i = 0; i < 30; i++) b[i] = 'a';
        b[30] = '"';
        b[31] = '\\';
        b[32] = '"'; // escaped quote — must NOT close string
        b[33] = '"'; // real closing quote
        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        assertTrue(r.isInsideString(31), "Backslash inside string");
        assertTrue(r.isInsideString(32), "Escaped quote inside string");
        assertFalse(r.isInsideString(33), "Real closing quote is NOT inside string");
    }

    @Test
    void inputLengthExactly64Bytes() {
        byte[] b = new byte[64];
        b[0] = '{';
        b[1] = '"'; b[2] = 'k'; b[3] = '"';
        b[4] = ':';
        b[5] = '1';
        b[6] = '}';
        // rest are spaces (whitespace, not structural)
        for (int i = 7; i < 64; i++) b[i] = ' ';

        ScanResult r = ScanResult.create(b.length);
        simd.scan(b, 0, b.length, r);

        assertTrue(r.isStructural(0));
        assertTrue(r.isStructural(4));
        assertTrue(r.isStructural(6));
        assertTrue(r.isQuote(1));
        assertTrue(r.isQuote(3));
        assertTrue(r.isInsideString(2));
    }
}