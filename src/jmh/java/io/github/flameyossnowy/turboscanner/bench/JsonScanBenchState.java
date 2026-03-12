package io.github.flameyossnowy.turboscanner.bench;

import io.github.flameyossnowy.turboscanner.ByteScanner;
import io.github.flameyossnowy.turboscanner.ByteUtf8Validator;
import io.github.flameyossnowy.turboscanner.Utf8Validator;
import io.github.flameyossnowy.turboscanner.VectorByteScanner;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class JsonScanBenchState {

    byte[] asciiJson;
    byte[] utf8Json;
    byte[] deepJson;
    byte[] stringHeavyJson;
    byte[] invalidUtf8;

    ByteScanner scanner;
    Utf8Validator utf8;

    @Setup(Level.Trial)
    public void setup() {
        scanner = new VectorByteScanner();
        utf8 = new ByteUtf8Validator();

        asciiJson = "{\"a\":1,\"b\":[true,false,null]}".getBytes(StandardCharsets.US_ASCII);

        utf8Json = """
            {"text":"こんにちは世界","emoji":"🔥🚀"}
            """.getBytes(StandardCharsets.UTF_8);

        deepJson = ("[".repeat(64) + "0" + "]".repeat(64))
                .getBytes(StandardCharsets.US_ASCII);

        stringHeavyJson = """
            {"a":"aaaaaaaaaa","b":"bbbbbbbbbb","c":"cccccccccc"}
            """.getBytes(StandardCharsets.US_ASCII);

        // Invalid continuation byte
        invalidUtf8 = new byte[] { (byte) 0xE2, (byte) 0x28, (byte) 0xA1 };
    }
}
