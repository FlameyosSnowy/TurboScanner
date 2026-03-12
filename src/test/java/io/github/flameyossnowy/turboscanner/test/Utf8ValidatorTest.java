package io.github.flameyossnowy.turboscanner.test;

import io.github.flameyossnowy.turboscanner.ByteUtf8Validator;
import io.github.flameyossnowy.turboscanner.Utf8Validator;
import io.github.flameyossnowy.turboscanner.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Utf8ValidatorTest {

    @Test
    void validUtf8() {
        Utf8Validator v = new ByteUtf8Validator();
        byte[] data = "こんにちは".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        v.validate(data, 0, data.length);
        assertFalse(v.hasError());
    }

    @Test
    void invalidUtf8() {
        Utf8Validator v = new ByteUtf8Validator();
        byte[] data = { (byte) 0xE2, (byte) 0x28, (byte) 0xA1 };

        v.validate(data, 0, data.length);
        assertTrue(v.hasError());
    }
}
