package me.flame.turboscanner;

import java.util.Arrays;

public final class ScanResult {
    final long[] quoteMask;
    final long[] backslashMask;
    final long[] controlMask;
    final long[] structuralMask;

    private final int lanes;

    private boolean utf8Error;

    public ScanResult(int lanes) {
        this.lanes = lanes;
        this.quoteMask = new long[lanes];
        this.backslashMask = new long[lanes];
        this.controlMask = new long[lanes];
        this.structuralMask = new long[lanes];
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

    public int getLanes() {
        return lanes;
    }

    public boolean isUtf8Error() {
        return utf8Error;
    }

    public void clear() {
        for (int i = 0; i < this.lanes; i++) {
            this.quoteMask[i] = 0L;
            this.backslashMask[i] = 0L;
            this.controlMask[i] = 0L;
            this.structuralMask[i] = 0L;
        }
        this.utf8Error = false;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ScanResult that)) return false;

        return masksEqual(this, that);
    }

    static boolean masksEqual(ScanResult first, ScanResult second) {
        if (first.lanes != second.lanes) return false;
        if (first.utf8Error != second.utf8Error) return false;

        for (int i = 0; i < first.lanes; i++) {
            long difference = (first.quoteMask[i] ^ second.quoteMask[i])
                    | (first.backslashMask[i] ^ second.backslashMask[i])
                    | (first.controlMask[i] ^ second.controlMask[i])
                    | (first.structuralMask[i] ^ second.structuralMask[i]);

            if (difference != 0) return false;
        }
        return true;
    }


    @Override
    public int hashCode() {
        int result = Arrays.hashCode(quoteMask);
        result = 31 * result + Arrays.hashCode(backslashMask);
        result = 31 * result + Arrays.hashCode(controlMask);
        result = 31 * result + Arrays.hashCode(structuralMask);
        result = 31 * result + lanes;
        result = 31 * result + Boolean.hashCode(utf8Error);
        return result;
    }
}
