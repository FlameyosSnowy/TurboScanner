package me.flame.turboscanner;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vectorized stage-1 JSON byte scanner.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Classify structural characters</li>
 *   <li>Detect quotes and backslashes</li>
 *   <li>Compute inside-string mask</li>
 * </ul>
 *
 * <p>This implementation mirrors simdjson-style stage-1 logic
 * adapted to the Java Vector API.
 */
public final class VectorByteScanner implements ByteScanner {
    private static final VectorSpecies<Byte> SPECIES =
        ByteVector.SPECIES_PREFERRED;

    private static final byte INPUT_SCALAR = (byte) 0x20;

    @Override
    public int scan(byte[] input, int offset, int length, ScanResult out) {
        int i = 0;
        int lanes = SPECIES.length();

        long prevInString = out.prevInString;
        long prevEndsWithBackslash = out.prevEndsWithBackslash;

        for (; i + lanes <= length; i += lanes) {
            ByteVector v = ByteVector.fromArray(SPECIES, input, offset + i);

            long quoteBits      = v.eq((byte) '"').toLong();
            long backslashBits  = v.eq((byte) '\\').toLong();
            long controlBits    = v.compare(VectorOperators.LT, INPUT_SCALAR).toLong();

            long structuralBits =
                v.eq((byte) '{')
                    .or(v.eq((byte) '}'))
                    .or(v.eq((byte) '['))
                    .or(v.eq((byte) ']'))
                    .or(v.eq((byte) ','))
                    .or(v.eq((byte) ':'))
                    .toLong();

            /* ---------------- Escaped quote suppression ---------------- */

            // Mark bytes escaped by a preceding backslash
            long escaped =
                ((backslashBits << 1) | prevEndsWithBackslash) & ~backslashBits;

            /* ---------------- Inside-string prefix XOR ---------------- */

            long inString = quoteBits & ~escaped;
            inString ^= inString << 1;
            inString ^= inString << 2;
            inString ^= inString << 4;
            inString ^= inString << 8;
            inString ^= inString << 16;
            inString ^= inString << 32;
            inString ^= prevInString;

            prevInString = (inString >>> 63) & 1L;

            // Update backslash carry
            long lastBackslash = backslashBits >>> 63;
            prevEndsWithBackslash =
                ((Long.bitCount(backslashBits) & 1) != 0) ? lastBackslash : 0;

            int word = i >>> 6;

            out.quoteMask[word]        = quoteBits;
            out.backslashMask[word]    = backslashBits;
            out.controlMask[word]      = controlBits;
            out.insideStringMask[word] = inString;
            out.structuralMask[word]   = structuralBits & ~inString;
        }

        out.prevInString = prevInString;
        out.prevEndsWithBackslash = prevEndsWithBackslash;

        return i;
    }
}
