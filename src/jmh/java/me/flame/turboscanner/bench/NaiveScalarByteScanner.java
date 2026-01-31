package me.flame.turboscanner.bench;

import me.flame.turboscanner.ByteScanner;
import me.flame.turboscanner.ScanResult;

public final class NaiveScalarByteScanner {

    /**
     * Best-case scalar scan:
     * - tight loop
     * - no bounds checks inside loop
     * - predictable branches
     */
    public int scan(byte[] data, int offset, int length, ScalarScanResult scan) {
        int count = 0;

        // cache length locally (JIT likes this)
        final int len = data.length;

        for (int i = 0; i < len; i++) {
            // Example: count structural JSON bytes
            // (branch is highly predictable on ASCII JSON)
            byte b = data[i];
            if (b == '{' || b == '}' || b == '[' || b == ']' || b == ',') {
                count++;
            }
        }

        return count;
    }
}
