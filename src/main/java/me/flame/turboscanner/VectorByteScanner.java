package me.flame.turboscanner;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.jetbrains.annotations.NotNull;

public final class VectorByteScanner implements ByteScanner {

    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    public static final int VLEN = SPECIES.length();
    private static final int CHUNK_SIZE = 512;
    private static final byte CONTROL_THRESHOLD = 0x20;
    private static final byte QUOTE    = (byte) '"';
    private static final byte BACKSLASH = (byte) '\\';

    // Precomputed per-lane-count masks.
    // LANE_MASKS[n] will contain a long value where the lowest `n` bits are set to 1.
    // Example:
    //   LANE_MASKS[0]  = 0b0
    //   LANE_MASKS[1]  = 0b1
    //   LANE_MASKS[2]  = 0b11
    //   LANE_MASKS[3]  = 0b111
    //   ...
    //   LANE_MASKS[64] = 0xFFFF_FFFF_FFFF_FFFFL (all 64 bits set)
    //
    // This avoids recomputing (1L << n) - 1 at runtime.
    private static final long[] LANE_MASKS = buildLaneMasks();

    private static long[] buildLaneMasks() {
        // Allocate array of size 65 to support masks for 0..64 bits inclusive.
        long[] m = new long[65];

        // For n in [0..63]:
        // (1L << n) creates a long with a single 1-bit at position n.
        // Subtracting 1 turns all lower bits into 1.
        //
        // Example:
        // n = 5
        // 1L << 5  = 0b100000  (32)
        // minus 1  = 0b011111  (31)
        //
        // So m[n] becomes a mask with the lowest n bits set.
        for (int i = 0; i < 64; i++) {
            m[i] = (1L << i) - 1L;
        }

        // Special case for 64:
        //
        // In Java, shifting a long by 64 does NOT work as expected.
        // Shift distance is masked with & 63, so:
        //
        // 1L << 64  == 1L << (64 % 64)
        //            == 1L << 0
        //            == 1
        //
        // That would break the formula.
        //
        // Instead, we manually assign -1L, which in two's complement
        // is represented as all 64 bits set to 1:
        //
        // 0xFFFF_FFFF_FFFF_FFFFL
        //
        // That is the correct 64-bit full mask.
        m[64] = -1L;

        return m;
    }

    @Override
    public int scan(byte[] input, int offset, int length, @NotNull ScanResult out) {
        if (length == 0) return 0;

        long prevInString     = out.prevInString;
        long prevBackslashOdd = out.prevEndsWithBackslash;

        final int simdLimit = length - (length % VLEN);
        final int tail      = length - simdLimit;

        int i = 0;
        while (i < simdLimit) {
            final int chunkEnd = Math.min(simdLimit, i + CHUNK_SIZE);
            while (i < chunkEnd) {
                final ByteVector vec = ByteVector.fromArray(SPECIES, input, offset + i);
                final long vlenMask  = LANE_MASKS[VLEN]; // -1L for VLEN=64, else correct mask

                long qMask = vec.eq(QUOTE).toLong();
                long bsMask = vec.eq(BACKSLASH).toLong();
                long ctrlMask = vec.compare(VectorOperators.LT, CONTROL_THRESHOLD).toLong();
                long strMask = vec.eq((byte)'{').or(vec.eq((byte)'}'))
                    .or(vec.eq((byte)'[')).or(vec.eq((byte)']'))
                    .or(vec.eq((byte)',')).or(vec.eq((byte)':')).toLong();

                long escaped   = computeEscapedPositions(bsMask, prevBackslashOdd);
                long toggles   = qMask & ~escaped;
                long inStrAfter = parallelPrefixXor(toggles)
                    ^ (prevInString != 0 ? -1L : 0L);
                inStrAfter     &= vlenMask;
                long inStrMask  = inStrAfter & ~toggles;

                prevInString     = (inStrAfter >>> (VLEN - 1)) & 1L;
                prevBackslashOdd = computeBackslashCarry(bsMask, VLEN - 1);

                // VLEN always divides evenly into 64-bit words when VLEN <= 64,
                // and pos is always a multiple of VLEN, so shift is always 0
                // when VLEN == 64, OR pos/VLEN lands on a word boundary when VLEN == 32.
                // writeMasked needed only for VLEN < 64.
                final int word  = i >>> 6;
                final int shift = i & 63;
                out.quoteMask[word]        |= (toggles          << shift);
                out.backslashMask[word]    |= (bsMask           << shift);
                out.controlMask[word]      |= (ctrlMask         << shift);
                out.structuralMask[word]   |= ((strMask & ~inStrMask) << shift);
                out.insideStringMask[word] |= (inStrMask        << shift);
                // spill upper bits if vector straddles a word boundary
                if (shift != 0) {
                    final int rshift = 64 - shift;
                    out.quoteMask[word+1]        |= (toggles          >>> rshift);
                    out.backslashMask[word+1]    |= (bsMask           >>> rshift);
                    out.controlMask[word+1]      |= (ctrlMask         >>> rshift);
                    out.structuralMask[word+1]   |= ((strMask & ~inStrMask) >>> rshift);
                    out.insideStringMask[word+1] |= (inStrMask        >>> rshift);
                }

                i += VLEN;
            }
        }

        if (tail > 0) {
            final ByteVector vec = ByteVector.fromArray(
                SPECIES, input, offset + i, SPECIES.indexInRange(0, tail));
            final long laneMask = LANE_MASKS[tail]; // exact tail mask, no branch

            long qMask    = vec.eq(QUOTE).toLong()                          & laneMask;
            long bsMask   = vec.eq(BACKSLASH).toLong()                      & laneMask;
            long ctrlMask = vec.compare(VectorOperators.LT, CONTROL_THRESHOLD).toLong() & laneMask;
            long strMask  = vec.eq((byte)'{').or(vec.eq((byte)'}'))
                .or(vec.eq((byte)'[')).or(vec.eq((byte)']'))
                .or(vec.eq((byte)',')).or(vec.eq((byte)':')).toLong() & laneMask;

            long escaped    = computeEscapedPositions(bsMask, prevBackslashOdd);
            long toggles    = qMask & ~escaped;
            long inStrAfter = (parallelPrefixXor(toggles) ^ (prevInString != 0 ? -1L : 0L))
                & laneMask;                 // clamp to tail, no branch
            long inStrMask  = inStrAfter & ~toggles;

            prevInString     = (inStrAfter >>> (tail - 1)) & 1L;
            prevBackslashOdd = computeBackslashCarry(bsMask, tail - 1);

            final int word  = i >>> 6;
            final int shift = i & 63;
            out.quoteMask[word]        |= (toggles               << shift);
            out.backslashMask[word]    |= (bsMask                << shift);
            out.controlMask[word]      |= (ctrlMask              << shift);
            out.structuralMask[word]   |= ((strMask & ~inStrMask)<< shift);
            out.insideStringMask[word] |= (inStrMask             << shift);
            if (shift != 0) {
                final int rshift = 64 - shift;
                out.quoteMask[word+1]        |= (toggles               >>> rshift);
                out.backslashMask[word+1]    |= (bsMask                >>> rshift);
                out.controlMask[word+1]      |= (ctrlMask              >>> rshift);
                out.structuralMask[word+1]   |= ((strMask & ~inStrMask)>>> rshift);
                out.insideStringMask[word+1] |= (inStrMask             >>> rshift);
            }
        }

        out.prevInString          = prevInString;
        out.prevEndsWithBackslash = prevBackslashOdd;
        return length;
    }

    private static long computeEscapedPositions(long bsMask, long prevCarry) {
        if (bsMask == 0 && prevCarry == 0) return 0L;
        long px      = parallelPrefixXor(bsMask) ^ (prevCarry != 0 ? -1L : 0L);
        long escapers = px & bsMask;
        long pos0     = prevCarry & ~bsMask & 1L;
        return (escapers << 1) | pos0;
    }

    private static long computeBackslashCarry(long bsMask, int lastPos) {
        long mask = bsMask & LANE_MASKS[lastPos + 1 > 63 ? 64 : lastPos + 1];
        if (mask == 0L) return 0L;
        int top = 63 - Long.numberOfLeadingZeros(mask);
        int cnt = 0;
        for (int k = top; k >= 0 && ((mask >>> k) & 1L) != 0; k--) cnt++;
        return cnt & 1L;
    }

    private static long parallelPrefixXor(long x) {
        x ^= x << 1;
        x ^= x << 2;
        x ^= x << 4;
        x ^= x << 8;
        x ^= x << 16;
        x ^= x << 32;
        return x;
    }
}