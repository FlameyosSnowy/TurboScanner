package me.flame.turboscanner.bench;

import me.flame.turboscanner.ScanResult;
import me.flame.turboscanner.test.JsonException;

public final class Validators {

    private Validators() {}

    /**
     * Validates that JSON structural characters are properly balanced.
     * <p>
     * Must be called after a successful stage-1 scan.
     * Structural characters inside strings are ignored by construction.
     */
    public static void validateStructure(byte[] input, ScanResult scan) {
        int depth = 0;
        int len = input.length;

        for (int i = 0; i < len; i++) {
            if (!scan.isStructural(i)) continue;

            switch (input[i]) {
                case '{', '[' -> depth++;
                case '}', ']' -> depth--;
            }

            if (depth < 0) {
                throw JsonException.unbalancedBrackets(i);
            }
        }

        if (depth != 0) {
            throw JsonException.unbalancedBrackets(len);
        }
    }

    /**
     * Validates string correctness:
     *  - no unterminated strings
     *  - no unescaped control characters inside strings
     */
    public static void validateStrings(byte[] input, ScanResult scan) {
        int len = input.length;

        for (int i = 0; i < len; i++) {
            if (!scan.isInsideString(i)) continue;

            byte b = input[i];

            // ASCII control characters are illegal inside JSON strings
            if (b < 0x20) {
                throw JsonException.unescapedControl();
            }
        }

        if (scan.isInsideString(len)) {
            throw JsonException.unterminatedString();
        }
    }
}
