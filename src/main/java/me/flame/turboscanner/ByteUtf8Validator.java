package me.flame.turboscanner;

import org.jetbrains.annotations.Contract;

/**
 * FastUtf8Validator performs forward-only UTF-8 validation over a byte array.
 *
 * <p>This validator detects:
 * <ul>
 *   <li>Invalid leading bytes</li>
 *   <li>Invalid continuation bytes</li>
 *   <li>Overlong sequences and illegal code points in a minimal way</li>
 * </ul>
 *
 * <p>This is a scalar, single-pass validator and is intended to be optionally composed
 * with a {@link ByteScanner} for safe scanning.
 */
@SuppressWarnings("unused")
public final class ByteUtf8Validator implements Utf8Validator {
    // Expected number of continuation bytes for the current UTF-8 sequence (0..3)
    private int expected;

    // Set to true if an invalid UTF-8 sequence is detected
    private boolean error;

    // Leading byte masks and expected lengths
    private static final int ONE_CONTINUATION = 1;   // for 2-byte sequence
    private static final int TWO_CONTINUATION = 2;   // for 3-byte sequence
    private static final int THREE_CONTINUATION = 3; // for 4-byte sequence

    // Smallest legal 2-byte lead byte (to avoid overlong encoding)
    private static final int MIN_TWO_BYTE_LEAD = 0xC2;

    // Largest legal 4-byte lead byte (to stay within Unicode range)
    private static final int MAX_FOUR_BYTE_LEAD = 0xF4;

    @Contract(mutates = "this")
    @Override
    public void reset() {
        expected = 0;
        error = false;
    }

    @Contract(pure = true)
    @Override
    public boolean hasError() {
        return error;
    }

    @Contract(mutates = "this")
    @Override
    public void validate(byte[] input, int offset, int length) {
        int i = offset;
        int end = offset + length;

        while (i < end) {
            int b = input[i++] & 0xFF; // treat byte as unsigned

            if (expected == 0) {
                // Determine the type of UTF-8 sequence
                if (b < 0x80) {
                    // ASCII (single-byte), nothing to do
                    continue;
                } else if ((b >> 5) == 0b110) {
                    // 2-byte sequence (110xxxxx)
                    expected = ONE_CONTINUATION;

                    // Avoid overlong encoding: lead byte must be >= 0xC2
                    if (b < MIN_TWO_BYTE_LEAD) {
                        error = true;
                    }
                } else if ((b >> 4) == 0b1110) {
                    // 3-byte sequence (1110xxxx)
                    expected = TWO_CONTINUATION;
                } else if ((b >> 3) == 0b11110) {
                    // 4-byte sequence (11110xxx)
                    expected = THREE_CONTINUATION;

                    // Must not exceed Unicode max
                    if (b > MAX_FOUR_BYTE_LEAD) {
                        error = true;
                    }
                } else {
                    // Invalid leading byte (11111xxx or 10xxxxxx in wrong place)
                    error = true;
                }
            } else {
                // Continuation byte check: must be 10xxxxxx
                if ((b >> 6) != 0b10) {
                    error = true;
                }
                expected--;
            }

            if (error) return; // early exit on first UTF-8 error
        }
    }
}