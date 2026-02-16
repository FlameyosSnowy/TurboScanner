package me.flame.turboscanner;

import org.jetbrains.annotations.Contract;

/**
 * ScanResult stores the output of a single stage-1 byte scan over a JSON byte stream.
 *
 * <p>This object is designed to be:
 * <ul>
 *   <li>Written sequentially by a {@link ByteScanner}</li>
 *   <li>Read many times by higher-level parsers</li>
 *   <li>Reused across scans to avoid allocations</li>
 * </ul>
 *
 * <h2>What this represents</h2>
 * Each mask is a bitset indexed by byte position:
 *
 * <pre>
 * bit i == 1  → property holds for byte i
 * </pre>
 *
 * Masks are grouped in 64-byte blocks:
 *
 * <pre>
 * word = byteIndex >>> 6
 * bit  = byteIndex & 63
 * </pre>
 *
 * <h2>Semantic guarantees</h2>
 * <ul>
 *   <li>{@code insideStringMask} correctly tracks JSON string state</li>
 *   <li>Escaped quotes are suppressed</li>
 *   <li>Structural characters are expected to be masked with {@code ~insideStringMask}</li>
 * </ul>
 *
 * <h2>What this does NOT do</h2>
 * <ul>
 *   <li>No UTF-8 validation</li>
 *   <li>No number parsing</li>
 *   <li>No structural nesting</li>
 * </ul>
 *
 * This is stage-1 only.
 *
 * <h2>Thread safety</h2>
 * Not thread-safe. One ScanResult per scan or per thread.
 */
@SuppressWarnings("unused")
public final class ScanResult {
    final long[] quoteMask;
    final long[] backslashMask;
    final long[] controlMask;
    final long[] structuralMask;
    final long[] insideStringMask;

    private final int lanes;

    // Cross-block carry
    long prevInString;
    long prevEndsWithBackslash;

    boolean utf8Error;

    private ScanResult(int lanes) {
        this.lanes = lanes;
        this.quoteMask = new long[lanes];
        this.backslashMask = new long[lanes];
        this.controlMask = new long[lanes];
        this.structuralMask = new long[lanes];
        this.insideStringMask = new long[lanes];
    }

    public static ScanResult create(int byteLength) {
        return new ScanResult(lanesFor(byteLength));
    }

    public static ScanResult createWithLanes(int lanes) {
        return new ScanResult(lanes);
    }

    @Contract(pure = true)
    public static int lanesFor(int byteLength) {
        return (byteLength + 63) >>> 6;
    }

    public void clear() {
        for (int i = 0; i < lanes; i++) {
            quoteMask[i] = 0L;
            backslashMask[i] = 0L;
            controlMask[i] = 0L;
            structuralMask[i] = 0L;
            insideStringMask[i] = 0L;
        }
        prevInString = 0L;
        prevEndsWithBackslash = 0L;
        utf8Error = false;
    }

    /* -------------------- Query helpers -------------------- */

    public boolean isInsideString(int index) {
        return ((insideStringMask[index >>> 6] >>> (index & 63)) & 1L) != 0;
    }

    public boolean isStructural(int index) {
        return ((structuralMask[index >>> 6] >>> (index & 63)) & 1L) != 0;
    }

    public boolean isQuote(int index) {
        return ((quoteMask[index >>> 6] >>> (index & 63)) & 1L) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ScanResult that)) return false;
        if (lanes != that.lanes) return false;
        if (utf8Error != that.utf8Error) return false;

        for (int i = 0; i < lanes; i++) {
            long diff =
                (quoteMask[i] ^ that.quoteMask[i]) |
                    (backslashMask[i] ^ that.backslashMask[i]) |
                    (controlMask[i] ^ that.controlMask[i]) |
                    (structuralMask[i] ^ that.structuralMask[i]) |
                    (insideStringMask[i] ^ that.insideStringMask[i]);

            if (diff != 0) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;

        for (int i = 0; i < lanes; i++) {
            long x = quoteMask[i] ^
                        backslashMask[i] ^
                        controlMask[i] ^
                        structuralMask[i] ^
                        insideStringMask[i];

            result = 31 * result + Long.hashCode(x);
        }

        result = 31 * result + lanes;
        result = 31 * result + (utf8Error ? 1 : 0);
        return result;
    }

    public long[] getQuoteMask() {
        return quoteMask;
    }

    public long[] getBackslashMask() {
        return backslashMask;
    }

    public long[] getControlMask() {
        return controlMask;
    }

    public long[] getStructuralMask() {
        return structuralMask;
    }

    public long[] getInsideStringMask() {
        return insideStringMask;
    }

    public int getLanes() {
        return lanes;
    }

    public long getPrevInString() {
        return prevInString;
    }

    public void setPrevInString(long prevInString) {
        this.prevInString = prevInString;
    }

    public long getPrevEndsWithBackslash() {
        return prevEndsWithBackslash;
    }

    public void setPrevEndsWithBackslash(long prevEndsWithBackslash) {
        this.prevEndsWithBackslash = prevEndsWithBackslash;
    }

    public boolean isUtf8Error() {
        return utf8Error;
    }

    public void setUtf8Error(boolean utf8Error) {
        this.utf8Error = utf8Error;
    }
}
