package io.github.flameyossnowy.turboscanner.bench;

/**
 * ScalarScanResult stores the output of a naive scalar JSON stage-1 scan.
 *
 * <p>This is a correctness and baseline structure, NOT SIMD-oriented.
 *
 * <h2>Properties</h2>
 * <ul>
 *   <li>One entry per byte</li>
 *   <li>Sequentially written</li>
 *   <li>No lane or block semantics</li>
 * </ul>
 *
 * <h2>What it tracks</h2>
 * <ul>
 *   <li>Quotes</li>
 *   <li>Backslashes</li>
 *   <li>Structural characters</li>
 *   <li>Inside-string state</li>
 * </ul>
 *
 * <h2>What it does NOT do</h2>
 * <ul>
 *   <li>No UTF-8 validation</li>
 *   <li>No escape suppression guarantees</li>
 *   <li>No SIMD parity promises</li>
 * </ul>
 *
 * This is scalar truth, not vector fantasy.
 */
@SuppressWarnings("unused")
public final class ScalarScanResult {

    public final boolean[] quote;
    public final boolean[] backslash;
    public final boolean[] structural;
    public final boolean[] insideString;

    public boolean utf8Error;

    public ScalarScanResult(int length) {
        this.quote = new boolean[length];
        this.backslash = new boolean[length];
        this.structural = new boolean[length];
        this.insideString = new boolean[length];
    }

    public void clear() {
        for (int i = 0; i < quote.length; i++) {
            quote[i] = false;
            backslash[i] = false;
            structural[i] = false;
            insideString[i] = false;
        }
        utf8Error = false;
    }

    /* -------------------- Queries -------------------- */

    public boolean isQuote(int i) {
        return quote[i];
    }

    public boolean isStructural(int i) {
        return structural[i];
    }

    public boolean isInsideString(int i) {
        return insideString[i];
    }
}
