package me.flame.turboscanner;

import org.jetbrains.annotations.Contract;

import java.util.Arrays;

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

        int words = lanes + 1;
        this.quoteMask = new long[words];
        this.backslashMask = new long[words];
        this.controlMask = new long[words];
        this.structuralMask = new long[words];
        this.insideStringMask = new long[words];
    }

    public static ScanResult create(int byteLength) {
        return new ScanResult(lanesFor(byteLength));
    }

    public static ScanResult createWithLanes(int lanes) {
        return new ScanResult(lanes);
    }

    @Contract(pure = true)
    public static int lanesFor(int byteLength) {
        return (byteLength + 63 + (VectorByteScanner.VLEN - 1)) >>> 6;
    }

    public void clear() {
        int words = lanes + 1;   // match constructor allocation
        for (int i = 0; i < words; i++) {
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

    // ── Batch iteration helpers ───────────────────────────────────────────────

    /**
     * Returns the raw 64-bit word of the inside-string mask for the word
     * containing {@code byteIndex}. The caller can then drain bits with
     * {@link Long#numberOfTrailingZeros} + clear-lowest-bit.
     *
     * <pre>
     * long word = r.insideStringWord(baseIndex);
     * while (word != 0) {
     *     int bit = Long.numberOfTrailingZeros(word);
     *     int bytePos = baseIndex + bit;
     *     // ... process bytePos ...
     *     word &= word - 1;  // clear lowest set bit
     * }
     * </pre>
     */
    public long insideStringWord(int byteIndex) {
        return insideStringMask[byteIndex >>> 6];
    }

    public long structuralWord(int byteIndex) {
        return structuralMask[byteIndex >>> 6];
    }

    public long quoteWord(int byteIndex) {
        return quoteMask[byteIndex >>> 6];
    }

    public long backslashWord(int byteIndex) {
        return backslashMask[byteIndex >>> 6];
    }

    public long controlWord(int byteIndex) {
        return controlMask[byteIndex >>> 6];
    }

    /**
     * Iterate all set bits across the entire mask, invoking {@code consumer}
     * with each byte position. Zero allocation; inner loop is a standard
     * bit-drain pattern the JIT reduces to BSF/BLSR on x86.
     */
    public void forEachStructural(java.util.function.IntConsumer consumer) {
        forEachSet(structuralMask, consumer);
    }

    public void forEachInsideString(java.util.function.IntConsumer consumer) {
        forEachSet(insideStringMask, consumer);
    }

    public void forEachQuote(java.util.function.IntConsumer consumer) {
        forEachSet(quoteMask, consumer);
    }

    private void forEachSet(long[] mask, java.util.function.IntConsumer consumer) {
        for (int w = 0; w < lanes; w++) {
            long word = mask[w];
            int base  = w << 6;
            while (word != 0) {
                consumer.accept(base + Long.numberOfTrailingZeros(word));
                word &= word - 1;
            }
        }
    }

    /**
     * Returns the next set bit at or after {@code fromByteIndex}, or -1 if none.
     * Useful for parsers that want to skip ahead to the next structural character.
     */
    public int nextStructural(int fromByteIndex) {
        return nextSet(structuralMask, fromByteIndex);
    }

    public int nextQuote(int fromByteIndex) {
        return nextSet(quoteMask, fromByteIndex);
    }

    private int nextSet(long[] mask, int from) {
        int  word = from >>> 6;
        int  bit  = from & 63;
        if (word >= lanes) return -1;

        // Mask off bits before `from` in the first word
        long w = mask[word] & (-1L << bit);
        while (true) {
            if (w != 0) return (word << 6) + Long.numberOfTrailingZeros(w);
            if (++word >= lanes) return -1;
            w = mask[word];
        }
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
            result = 31 * result + Long.hashCode(quoteMask[i]);
            result = 31 * result + Long.hashCode(backslashMask[i]);
            result = 31 * result + Long.hashCode(controlMask[i]);
            result = 31 * result + Long.hashCode(insideStringMask[i]);
            result = 31 * result + Long.hashCode(structuralMask[i]);
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

    @Override
    public String toString() {
        return "ScanResult{" +
            "quoteMask=" + Arrays.toString(quoteMask) +
            ", backslashMask=" + Arrays.toString(backslashMask) +
            ", controlMask=" + Arrays.toString(controlMask) +
            ", structuralMask=" + Arrays.toString(structuralMask) +
            ", insideStringMask=" + Arrays.toString(insideStringMask) +
            ", lanes=" + lanes +
            ", prevInString=" + prevInString +
            ", prevEndsWithBackslash=" + prevEndsWithBackslash +
            ", utf8Error=" + utf8Error +
            '}';
    }
}
