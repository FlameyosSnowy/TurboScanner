package io.github.flameyossnowy.turboscanner;

@SuppressWarnings("unused")
public final class ScanWindow {
    public static final int CHUNK_SIZE = 4096;

    // Refill when fewer than this many bytes remain in the window.
    // Sized to cover the longest realistic single JSON value (large string).
    private static final int LOOKAHEAD  = 512;

    // Words per chunk: CHUNK_SIZE / 64
    private static final int WORDS = CHUNK_SIZE >>> 6;

    private final byte[]           input;
    private final int              inputLen;
    private final VectorByteScanner scanner;

    private final ScanResult result;

    private int windowBase;

    // How many bytes are valid in the current window
    // (== CHUNK_SIZE except for the last chunk).
    private int windowLen;

    public ScanWindow(byte[] input) {
        this.input    = input;
        this.inputLen = input.length;
        this.scanner  = new VectorByteScanner();
        // Allocate with WORDS+1 to absorb spill writes in VectorByteScanner
        this.result   = ScanResult.createWithLanes(WORDS + 1);
        this.windowBase = -1; // sentinel: not yet scanned
    }

    /**
     * Ensure the window covers [pos, pos + LOOKAHEAD).
     * Called by JsonCursor before any bitmask lookup.
     * No-op if the window already covers pos.
     */
    public void ensureCovered(int pos) {
        if (windowBase >= 0
                && pos >= windowBase
                && pos < windowBase + windowLen - LOOKAHEAD) {
            return; // hot path: already covered
        }
        refill(pos);
    }

    private void refill(int pos) {
        // Align window base to CHUNK_SIZE boundary
        int newBase = (pos / CHUNK_SIZE) * CHUNK_SIZE;
        int newLen  = Math.min(CHUNK_SIZE, inputLen - newBase);
        if (newLen <= 0) {
            windowBase = newBase;
            windowLen  = 0;
            return;
        }

        result.clear();
        scanner.scan(input, newBase, newLen, result);

        windowBase = newBase;
        windowLen  = newLen;
    }

    /** Convert absolute position to word index within the current window. */
    private int word(int absPos) {
        return (absPos - windowBase) >>> 6;
    }

    /** Convert absolute position to bit index within its word. */
    private static int bit(int absPos) {
        return absPos & 63;
    }

    public boolean isInsideString(int absPos) {
        ensureCovered(absPos);
        return ((result.insideStringMask[word(absPos)] >>> bit(absPos)) & 1L) != 0;
    }

    public boolean isStructural(int absPos) {
        ensureCovered(absPos);
        return ((result.structuralMask[word(absPos)] >>> bit(absPos)) & 1L) != 0;
    }

    public boolean isQuote(int absPos) {
        ensureCovered(absPos);
        return ((result.quoteMask[word(absPos)] >>> bit(absPos)) & 1L) != 0;
    }

    /**
     * Returns the next set a bit in structuralMask at or after absPos,
     * or -1 if none within the current window.
     * Caller must refill and retry if -1 and not at end of input.
     */
    public int nextStructural(int absPos) {
        ensureCovered(absPos);
        int relPos = absPos - windowBase;
        int wIdx   = relPos >>> 6;
        int wEnd   = (windowLen + 63) >>> 6; // words covering the window

        long w = result.structuralMask[wIdx] & (-1L << (relPos & 63));
        while (true) {
            if (w != 0) return windowBase + (wIdx << 6) + Long.numberOfTrailingZeros(w);
            if (++wIdx >= wEnd) return -1;
            w = result.structuralMask[wIdx];
        }
    }

    /**
     * Returns the next set a bit in quoteMask at or after absPos,
     * skipping positions where isInsideString is true (i.e. escaped quotes).
     * Returns -1 if none in this window.
     */
    public int nextUnescapedQuote(int absPos) {
        ensureCovered(absPos);
        int relPos = absPos - windowBase;
        int wIdx   = relPos >>> 6;
        int wEnd   = (windowLen + 63) >>> 6;

        long w = result.quoteMask[wIdx] & (-1L << (relPos & 63));
        while (true) {
            if (w != 0) {
                int bit = Long.numberOfTrailingZeros(w);
                return windowBase + (wIdx << 6) + bit;
            }
            if (++wIdx >= wEnd) return -1;
            w = result.quoteMask[wIdx];
        }
    }

    /**
     * Drain all structural character positions in [absFrom, absTo)
     * into a caller-supplied IntConsumer. Used by skipValueEnd batch path.
     * Automatically refills if the range spans a chunk boundary.
     */
    public void forEachStructuralInRange(int absFrom, int absTo,
                                          java.util.function.IntConsumer consumer) {
        int pos = absFrom;
        while (pos < absTo) {
            ensureCovered(pos);
            int chunkEnd = Math.min(absTo, windowBase + windowLen);
            int relFrom  = pos - windowBase;
            int wStart   = relFrom >>> 6;
            int wEnd     = ((chunkEnd - windowBase) + 63) >>> 6;

            for (int wi = wStart; wi < wEnd; wi++) {
                long mask = result.structuralMask[wi];
                if (wi == wStart) mask &= -1L << (relFrom & 63);
                int base = windowBase + (wi << 6);
                while (mask != 0) {
                    int b = Long.numberOfTrailingZeros(mask);
                    int abs = base + b;
                    if (abs < absTo) consumer.accept(abs);
                    mask &= mask - 1;
                }
            }
            pos = chunkEnd;
        }
    }

    public int getWindowBase()  { return windowBase; }
    public int getWindowLen()   { return windowLen;  }
    public ScanResult getResult() { return result;   }
}
