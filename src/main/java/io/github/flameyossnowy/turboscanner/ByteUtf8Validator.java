package io.github.flameyossnowy.turboscanner;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.jetbrains.annotations.Contract;

/**
 * SIMD-accelerated UTF-8 validator for JSON-style data.
 *
 * <p>This validator:
 * - Fast-paths ASCII-heavy blocks using SIMD
 * - Detects non-ASCII and validates multi-byte sequences
 * - Uses a branchless scalar DFA fallback for remaining bytes
 * - Minimizes branching while keeping code readable
 */
public final class ByteUtf8Validator implements Utf8Validator {

    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final int VLEN = SPECIES.length();

    /** DFA state machine for multi-byte sequences */
    private static final byte[][] STATE_TABLE = buildStateTable();

    /** Byte classification for DFA */
    private static final byte[] BYTE_TYPE = buildByteTypeTable();

    /** Current DFA state */
    private int state;

    /** Error flag */
    private boolean error;

    @Contract(mutates = "this")
    @Override
    public void reset() {
        state = 0;
        error = false;
    }

    @Contract(pure = true)
    @Override
    public boolean hasError() {
        return error || state != 0;
    }

    @Contract(mutates = "this")
    @Override
    public void validate(byte[] input, int offset, int length) {
        if (error) return;

        int i = offset;
        int end = offset + length;

        // -------------------------
        // SIMD fast-path: skip ASCII
        // -------------------------
        while (i + VLEN <= end && state == 0) {
            ByteVector vec = ByteVector.fromArray(SPECIES, input, i);

            // MSB 0 means ASCII
            long nonAsciiMask = vec.compare(VectorOperators.LT, (byte) 0).toLong();
            if (nonAsciiMask == 0) {
                i += VLEN; // all ASCII, skip
            } else {
                break; // non-ASCII detected
            }
        }

        // -------------------------
        // Branchless scalar DFA fallback
        // -------------------------
        int localState = state;

        // Process 4 bytes per loop for ILP
        for (; i + 4 <= end; i += 4) {
            localState = nextState(input[i], localState);
            localState = nextState(input[i + 1], localState);
            localState = nextState(input[i + 2], localState);
            localState = nextState(input[i + 3], localState);

            if (localState >= 13) {
                error = true;
                return;
            }
        }

        // Remaining bytes
        for (; i < end; i++) {
            localState = nextState(input[i], localState);
            if (localState >= 13) {
                error = true;
                return;
            }
        }

        state = localState;
    }

    // ------------------------------------
    // DFA helpers
    // ------------------------------------
    private static int nextState(byte b, int currentState) {
        int unsigned = b & 0xFF;
        int type = BYTE_TYPE[unsigned];
        return STATE_TABLE[currentState][type];
    }

    private static byte[][] buildStateTable() {
        byte[][] table = new byte[14][10];
        // State 0 = accept
        table[0][0] = 0; // ASCII -> accept
        table[0][1] = 13; // continuation -> error
        table[0][2] = 1;  // 2-byte lead
        table[0][3] = 2;  // 3-byte lead
        table[0][4] = 3;  // 4-byte lead
        table[0][9] = 13; // invalid

        // State 1: expect 1 continuation (2-byte)
        table[1][0] = 13;
        table[1][1] = 0; // accept
        for (int i = 2; i < 10; i++) table[1][i] = 13;

        // State 2: expect 2 continuations (3-byte)
        table[2][0] = 13;
        table[2][1] = 4; // 1 more
        for (int i = 2; i < 10; i++) table[2][i] = 13;

        // State 3: expect 3 continuations (4-byte)
        table[3][0] = 13;
        table[3][1] = 5; // 2 more
        for (int i = 2; i < 10; i++) table[3][i] = 13;

        // State 4: expecting last continuation of 3-byte
        table[4][0] = 13;
        table[4][1] = 0;
        for (int i = 2; i < 10; i++) table[4][i] = 13;

        // State 5: expecting 2nd continuation of 4-byte
        table[5][0] = 13;
        table[5][1] = 6;
        for (int i = 2; i < 10; i++) table[5][i] = 13;

        // State 6: expecting last continuation of 4-byte
        table[6][0] = 13;
        table[6][1] = 0;
        for (int i = 2; i < 10; i++) table[6][i] = 13;

        // States 7-12: reserved for future checks
        for (int s = 7; s < 13; s++)
            for (int t = 0; t < 10; t++)
                table[s][t] = 13;

        // State 13 = error sink
        for (int t = 0; t < 10; t++) table[13][t] = 13;

        return table;
    }

    private static byte[] buildByteTypeTable() {
        byte[] type = new byte[256];
        for (int i = 0; i < 256; i++) {
            if (i < 0x80) type[i] = 0; // ASCII
            else if ((i & 0xC0) == 0x80) type[i] = 1; // continuation
            else if ((i & 0xE0) == 0xC0) type[i] = (byte) (i < 0xC2 ? 9 : 2); // 2-byte lead
            else if ((i & 0xF0) == 0xE0) type[i] = 3; // 3-byte lead
            else if ((i & 0xF8) == 0xF0) type[i] = (byte) (i > 0xF4 ? 9 : 4); // 4-byte lead
            else type[i] = 9; // invalid
        }
        return type;
    }
}
