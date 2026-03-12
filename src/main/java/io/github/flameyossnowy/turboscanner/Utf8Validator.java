package io.github.flameyossnowy.turboscanner;

/**
 * Validates UTF-8 byte sequences in a forward-only scan.
 *
 * <p>This validator is designed to be composed with {@link ByteScanner}
 * or run as a separate pass.
 *
 * <p>It does not decode code points. It only validates structure.
 */
public interface Utf8Validator {
    void reset();

    void validate(byte[] input, int offset, int length);

    boolean hasError();
}
