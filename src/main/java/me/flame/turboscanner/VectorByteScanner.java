package me.flame.turboscanner;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.jetbrains.annotations.NotNull;

/**
 * High-performance stage-1 JSON byte scanner using SIMD.
 *
 * <p>This scanner:
 * <ul>
 *   <li>Classifies quotes, backslashes, control chars, and structural chars</li>
 *   <li>Computes inside-string mask with branchless prefix XOR</li>
 *   <li>Uses cache-line aligned chunking to reduce memory latency</li>
 *   <li>Falls back to scalar processing for remaining bytes</li>
 * </ul>
 */
public final class VectorByteScanner implements ByteScanner {

    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final int VLEN = SPECIES.length();
    private static final int CHUNK_SIZE = 512; // process 8 cache lines at a time
    private static final byte CONTROL_THRESHOLD = 0x20;
    private static final byte QUOTE = (byte) '"';
    private static final byte BACKSLASH = (byte) '\\';

    @Override
    public int scan(byte[] input, int offset, int length, @NotNull ScanResult out) {
        if (length == 0) return 0;

        long prevInString = out.prevInString;
        long prevBackslashOdd = out.prevEndsWithBackslash;
        int i = 0;

        final int simdLimit = length - (length % VLEN);

        // Process input in chunks to improve cache locality
        while (i < simdLimit) {
            int chunkLen = Math.min(simdLimit - i, CHUNK_SIZE);
            int chunkEnd = i + chunkLen;

            for (int pos = i; pos < chunkEnd; pos += VLEN) {
                ByteVector vec = ByteVector.fromArray(SPECIES, input, offset + pos);

                // Extract masks for quote, backslash, control, structural chars
                long quoteMask = detectQuotes(vec);
                long backslashMask = detectBackslashes(vec);
                long controlMask = detectControlChars(vec);
                long structuralMask = detectStructuralChars(vec);

                // Compute escaped quotes (branchless)
                long escapedQuotes = computeEscapedQuotes(backslashMask, prevBackslashOdd);
                long toggles = quoteMask & ~escapedQuotes;

                // Compute inside-string mask with parallel prefix XOR
                long inStringMask = parallelPrefixXor(toggles) ^ (prevInString != 0 ? -1L : 0L);

                // Update carries for next vector
                prevInString = extractLowBit(inStringMask, VLEN - 1);
                prevBackslashOdd = computeBackslashCarry(backslashMask, VLEN - 1);

                // Batched write to output
                int word = pos >>> 6;
                out.quoteMask[word] |= quoteMask;
                out.backslashMask[word] |= backslashMask;
                out.controlMask[word] |= controlMask;
                out.structuralMask[word] |= structuralMask & ~inStringMask;
                out.insideStringMask[word] |= inStringMask;
            }

            i = chunkEnd;
        }

        // Scalar fallback for remaining bytes
        if (i < length) {
            int processed = NaiveScalarScan.scan(
                input, offset + i, length - i, out, prevInString, prevBackslashOdd);
            i += processed;
            prevInString = out.prevInString;
            prevBackslashOdd = out.prevEndsWithBackslash;
        }

        out.prevInString = prevInString;
        out.prevEndsWithBackslash = prevBackslashOdd;

        return i;
    }

    /* ============================================================
     * Vector classification methods
     * ============================================================ */

    private static long detectQuotes(@NotNull ByteVector vec) {
        return vec.eq(QUOTE).toLong();
    }

    private static long detectBackslashes(@NotNull ByteVector vec) {
        return vec.eq(BACKSLASH).toLong();
    }

    private static long detectControlChars(@NotNull ByteVector vec) {
        return vec.compare(VectorOperators.LT, CONTROL_THRESHOLD).toLong();
    }

    private static long detectStructuralChars(@NotNull ByteVector vec) {
        return vec.eq((byte) '{')
            .or(vec.eq((byte) '}'))
            .or(vec.eq((byte) '['))
            .or(vec.eq((byte) ']'))
            .or(vec.eq((byte) ','))
            .or(vec.eq((byte) ':'))
            .toLong();
    }

    /* ============================================================
     * Escape and string handling
     * ============================================================ */

    /** Branchless propagation of escaped quotes using prefix XOR */
    private static long computeEscapedQuotes(long backslashMask, long prevBackslashOdd) {
        long bs = (backslashMask << 1) | prevBackslashOdd;
        // Parallel prefix XOR to detect odd-numbered sequences
        bs ^= bs << 1;
        bs ^= bs << 2;
        bs ^= bs << 4;
        bs ^= bs << 8;
        bs ^= bs << 16;
        bs ^= bs << 32;
        return bs & backslashMask; // only positions that actually escape quotes
    }

    /** Parallel prefix XOR to compute inside-string state toggles */
    private static long parallelPrefixXor(long toggles) {
        long x = toggles;
        x ^= x << 1;
        x ^= x << 2;
        x ^= x << 4;
        x ^= x << 8;
        x ^= x << 16;
        x ^= x << 32;
        return x;
    }

    /** Extract a bit at a specific position */
    private static long extractLowBit(long mask, int pos) {
        return (mask >>> pos) & 1L;
    }

    /** Count trailing backslashes to compute carry into next vector */
    private static long computeBackslashCarry(long backslashMask, int lastPos) {
        long mask = backslashMask & ((1L << (lastPos + 1)) - 1);
        if (mask == 0) return 0;
        int lastSet = 63 - Long.numberOfLeadingZeros(mask);
        int count = 0;
        for (int i = lastSet; i >= 0; i--) {
            if (((mask >>> i) & 1) != 0) count++;
            else break;
        }
        return count & 1;
    }
}