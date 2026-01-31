package me.flame.turboscanner;

/**
 * ByteScanner performs a single forward scan over a byte array
 * and classifies bytes into semantic categories.
 *
 * <h2>Intended usage</h2>
 *
 * <pre>{@code
 * ScanResult result = new ScanResult(words);
 * scanner.scan(bytes, 0, length, result);
 *
 * // Stage-2: structural index extraction, parsing, etc.
 * }</pre>
 *
 * <h2>Design constraints</h2>
 * <ul>
 *   <li>Must be linear, forward-only</li>
 *   <li>No allocation during scan</li>
 *   <li>No rescanning of bytes</li>
 * </ul>
 *
 * <h2>Composition</h2>
 * This scanner is expected to be combined with:
 * <ul>
 *   <li>A UTF-8 validator (optional but recommended)</li>
 *   <li>A structural index builder</li>
 *   <li>A stage-2 JSON parser</li>
 * </ul>
 *
 * <h2>Safety</h2>
 * Implementations must not read outside {@code [offset, offset+length)}.
 */
@SuppressWarnings("unused")
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
