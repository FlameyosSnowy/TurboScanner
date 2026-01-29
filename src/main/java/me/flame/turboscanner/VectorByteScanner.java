package me.flame.turboscanner;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public final class VectorByteScanner implements ByteScanner {
    private static final VectorSpecies<Byte> SPECIES =
        ByteVector.SPECIES_PREFERRED;

    @Override
    public int scan(byte[] input, int offset, int length, ScanResult out) {
        int i = 0;
        int lanes = SPECIES.length();

        for (; i + lanes <= length; i += lanes) {
            var v = ByteVector.fromArray(SPECIES, input, offset + i);

            long quoteBits =
                v.eq((byte) '"').toLong();

            long backslashBits =
                v.eq((byte) '\\').toLong();

            long controlBits =
                v.compare(VectorOperators.LT, (byte) 0x20).toLong();

            long structuralBits =
                v.eq((byte) '{')
                 .or(v.eq((byte) '}'))
                 .or(v.eq((byte) '['))
                 .or(v.eq((byte) ']'))
                 .or(v.eq((byte) ','))
                 .or(v.eq((byte) ':'))
                 .toLong();

            int word = i >>> 6;
            out.quoteMask[word] = quoteBits;
            out.backslashMask[word] = backslashBits;
            out.controlMask[word] = controlBits;
            out.structuralMask[word] = structuralBits;
        }

        return i;
    }
}
