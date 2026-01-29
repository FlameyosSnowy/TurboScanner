package me.flame.turboscanner;

public interface ByteScanner {
    /**
     * Scans a UTF-8 byte buffer and produces classification masks.
     *
     * @param input   byte buffer
     * @param offset  starting index (inclusive)
     * @param length  number of bytes to scan
     * @param out     destination for scan results
     *
     * @return number of bytes actually scanned
     */
    int scan(byte[] input, int offset, int length, ScanResult out);
}
