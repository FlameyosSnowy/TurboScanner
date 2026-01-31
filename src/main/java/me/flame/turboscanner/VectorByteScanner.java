package me.flame.turboscanner;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Vectorized stage-1 JSON byte scanner.
 *
 * <p>This scanner performs a single forward pass over a byte array and
 * classifies bytes into semantic categories needed for JSON parsing.
 *
 * <h2>Stage-1 responsibilities</h2>
 * <ul>
 *   <li>Detect quotes and backslashes</li>
 *   <li>Compute inside-string state (with escape handling)</li>
 *   <li>Identify structural characters outside strings</li>
 * </ul>
 *
 * <p>No UTF-8 decoding or parsing is performed here.
 * This stage is designed to be combined with:
 * <ul>
 *   <li>a UTF-8 validator (optional)</li>
 *   <li>a structural index builder (stage-2)</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class VectorByteScanner implements ByteScanner {
    private static final VectorSpecies<Byte> SPECIES =
        ByteVector.SPECIES_PREFERRED;

    /** ASCII control characters are &lt; 0x20 */
    private static final byte CONTROL_THRESHOLD = 0x20;

    @Override
    public int scan(byte[] input, int offset, int length, @NotNull ScanResult out) {
        int i = 0;
        int lanes = SPECIES.length();

        long prevInString = out.prevInString;
        long prevEndsWithBackslash = out.prevEndsWithBackslash;

        for (; i + lanes <= length; i += lanes) {
            ByteVector vector = loadVector(input, offset + i);

            long quoteBits     = detectQuotes(vector);
            long backslashBits = detectBackslashes(vector);
            long controlBits   = detectControlCharacters(vector);
            long structuralBits = detectStructuralCharacters(vector);

            long escapedBits =
                computeEscapedBytes(backslashBits, prevEndsWithBackslash);

            long inStringMask =
                computeInsideStringMask(quoteBits, escapedBits, prevInString);

            prevInString =
                extractStringCarry(inStringMask);

            prevEndsWithBackslash =
                extractBackslashCarry(backslashBits);

            int word = i >>> 6;

            writeMasks(out, word,
                quoteBits,
                backslashBits,
                controlBits,
                structuralBits,
                inStringMask
            );
        }

        out.prevInString = prevInString;
        out.prevEndsWithBackslash = prevEndsWithBackslash;

        return i;
    }

    /* ============================================================
     * Vector loading
     * ============================================================ */

    /** Loads a SIMD vector from the input byte array. */
    private static ByteVector loadVector(byte[] input, int index) {
        return ByteVector.fromArray(SPECIES, input, index);
    }

    /* ============================================================
     * Byte classification
     * ============================================================ */

    /** Detects {@code '"'} characters. */
    private static long detectQuotes(@NotNull ByteVector v) {
        return v.eq((byte) '"').toLong();
    }

    /** Detects {@code '\\'} characters. */
    private static long detectBackslashes(@NotNull ByteVector v) {
        return v.eq((byte) '\\').toLong();
    }

    /** Detects ASCII control characters (byte &lt; 0x20). */
    private static long detectControlCharacters(@NotNull ByteVector v) {
        return v.compare(VectorOperators.LT, CONTROL_THRESHOLD).toLong();
    }

    /**
     * Detects JSON structural characters:
     * <pre>{ } [ ] , :</pre>
     *
     * <p>This mask still includes characters inside strings and must
     * be suppressed later using the inside-string mask.
     */
    private static long detectStructuralCharacters(@NotNull ByteVector v) {
        return v.eq((byte) '{')
            .or(v.eq((byte) '}'))
            .or(v.eq((byte) '['))
            .or(v.eq((byte) ']'))
            .or(v.eq((byte) ','))
            .or(v.eq((byte) ':'))
            .toLong();
    }

    /* ============================================================
     * String and escape handling
     * ============================================================ */

    /**
     * Computes which bytes are escaped by a preceding backslash.
     *
     * <p>A byte is considered escaped if:
     * <ul>
     *   <li>it immediately follows a backslash</li>
     *   <li>that backslash itself is not escaped</li>
     * </ul>
     *
     * <p>{@code prevEndsWithBackslash} carries escape state across vector boundaries.
     */
    @Contract(pure = true)
    private static long computeEscapedBytes(long backslashBits, long prevEndsWithBackslash) {
        return ((backslashBits << 1) | prevEndsWithBackslash) & ~backslashBits;
    }

    /**
     * Computes the inside-string mask using prefix XOR.
     *
     * <p>The mask toggles state on every <em>unescaped</em> quote.
     * The resulting bit is 1 for bytes inside a JSON string.
     */
    @Contract(pure = true)
    private static long computeInsideStringMask(
        long quoteBits,
        long escapedBits,
        long prevInString
    ) {
        long inString = quoteBits & ~escapedBits;
        inString ^= inString << 1;
        inString ^= inString << 2;
        inString ^= inString << 4;
        inString ^= inString << 8;
        inString ^= inString << 16;
        inString ^= inString << 32;

        return inString ^ prevInString;
    }

    /**
     * Extracts the carry bit indicating whether the scan ends inside a string.
     */
    private static long extractStringCarry(long inStringMask) {
        return (inStringMask >>> 63) & 1L;
    }

    /**
     * Extracts whether the vector ends with an unpaired backslash.
     *
     * <p>This is required to correctly escape the first byte of the next vector.
     */
    private static long extractBackslashCarry(long backslashBits) {
        long lastBit = backslashBits >>> 63;
        return ((Long.bitCount(backslashBits) & 1) != 0) ? lastBit : 0;
    }

    /* ============================================================
     * Output
     * ============================================================ */

    /**
     * Writes all computed masks into the ScanResult.
     *
     * <p>Structural characters inside strings are suppressed here.
     */
    private static void writeMasks(
        @NotNull ScanResult out,
        int word,
        long quoteBits,
        long backslashBits,
        long controlBits,
        long structuralBits,
        long inStringMask
    ) {
        out.quoteMask[word]        = quoteBits;
        out.backslashMask[word]    = backslashBits;
        out.controlMask[word]      = controlBits;
        out.insideStringMask[word] = inStringMask;
        out.structuralMask[word]   = structuralBits & ~inStringMask;
    }
}