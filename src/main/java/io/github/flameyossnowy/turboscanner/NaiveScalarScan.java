package io.github.flameyossnowy.turboscanner;

import org.jetbrains.annotations.NotNull;

/**
 * Hyper-optimized scalar JSON byte scanner.
 * <p> This scanner is used as fallback when SIMD isn't applicable
 * (tail bytes, also scalar is faster for non-ASCII regions). It's optimized to be nearly as fast
 * as SIMD for small runs.
 *
 * <h2>Optimizations</h2>
 * <ul>
 *   <li>Branchless character classification using bitmasks</li>
 *   <li>Escape state tracking without branching</li>
 *   <li>Batch bit updates to minimize memory writes</li>
 *   <li>Structural char detection via lookup table</li>
 *   <li>Minimized state transitions</li>
 * </ul>
 */
public final class NaiveScalarScan {
    private static final byte CONTROL_THRESHOLD = 0x20;

    // Lookup table: is byte a structural character?
    // Access pattern: STRUCTURAL_LUT[b & 0x7F] for ASCII
    private static final long STRUCTURAL_LUT_LOW;
    private static final long STRUCTURAL_LUT_HIGH;

    static {
        // Build bitmask for structural characters in ASCII range
        long low = 0;
        low |= (1L << ',');  // 0x2C
        low |= (1L << ':');  // 0x3A
        low |= (1L << '[');  // 0x5B
        low |= (1L << ']');  // 0x5D
        STRUCTURAL_LUT_LOW = low;

        long high = 0;
        high |= (1L << ('{' - 64));  // 0x7B
        high |= (1L << ('}' - 64));  // 0x7D
        STRUCTURAL_LUT_HIGH = high;
    }

    /**
     * Scan with aggressive unrolling and branchless ops.
     */
    public static int scan(
        byte[] input,
        int offset,
        int length,
        @NotNull ScanResult out,
        long prevInString,
        long prevEndsWithBackslash
    ) {
        long inString = prevInString & 1L;
        long backslashOdd = prevEndsWithBackslash & 1L;

        int currentWord = offset >>> 6;
        long quoteBatch = 0;
        long backslashBatch = 0;
        long controlBatch = 0;
        long structuralBatch = 0;
        long insideStringBatch = 0;

        long[] inStringCarry = new long[]{inString, backslashOdd};

        for (int i = 0; i < length; i++) {
            int idx = offset + i;
            int word = idx >>> 6;
            int bit = idx & 63;
            long mask = 1L << bit;

            // Flush batch if word changes
            if (word != currentWord) {
                flushBatch(out, currentWord, quoteBatch, backslashBatch,
                    controlBatch, structuralBatch, insideStringBatch);
                currentWord = word;
                quoteBatch = backslashBatch = controlBatch = structuralBatch = insideStringBatch = 0;
            }

            byte b = input[idx];
            int unsigned = b & 0xFF;

            long isQuote = ((unsigned ^ '"') - 1) >>> 31;
            long isBackslash = ((unsigned ^ '\\') - 1) >>> 31;
            long isControl = (CONTROL_THRESHOLD - unsigned - 1) >>> 31;
            long isStructural = isStructuralBranchless(unsigned);

            // Update string state branchless
            long escaped = inStringCarry[1];
            inStringCarry[1] = isBackslash;
            long toggles = isQuote & ~escaped;
            inStringCarry[0] ^= toggles;

            // Batch update
            quoteBatch |= isQuote & mask;
            backslashBatch |= isBackslash & mask;
            controlBatch |= isControl & mask;
            structuralBatch |= isStructural & mask;
            insideStringBatch |= (inStringCarry[0] & mask);
        }

        // Final flush
        flushBatch(out, currentWord, quoteBatch, backslashBatch,
            controlBatch, structuralBatch, insideStringBatch);

        // Update carries
        out.prevInString = inStringCarry[0] & 1L;
        out.prevEndsWithBackslash = inStringCarry[1] & 1L;

        return length;
    }

    /**
     * Branchless structural character detection.
     * Returns 1 if structural, 0 otherwise.
     */
    private static long isStructuralBranchless(int unsigned) {
        // Check against low bits (< 64)
        long lowMatch = (unsigned < 64) ? ((STRUCTURAL_LUT_LOW >>> unsigned) & 1L) : 0;

        // Check against high bits (64-127)
        long highMatch = (unsigned >= 64 && unsigned < 128)
            ? ((STRUCTURAL_LUT_HIGH >>> (unsigned - 64)) & 1L) : 0;

        return lowMatch | highMatch;
    }

    /**
     * Flush accumulated bits to output masks.
     * Single write per mask minimizes cache pollution.
     */
    private static void flushBatch(@NotNull ScanResult out, int word,
                                   long quoteBatch, long backslashBatch,
                                   long controlBatch, long structuralBatch,
                                   long insideStringBatch) {
        out.quoteMask[word] |= quoteBatch;
        out.backslashMask[word] |= backslashBatch;
        out.controlMask[word] |= controlBatch;
        out.structuralMask[word] |= structuralBatch;
        out.insideStringMask[word] |= insideStringBatch;
    }
}