package io.github.sinri.Rahab.v2.transform;

import io.vertx.core.buffer.Buffer;

import java.util.function.Function;

public abstract class WormholeTransformPair {
    public final Function<Buffer, Buffer> getEncoder() {
        return this::encode;
    }

    public final Function<Buffer, Buffer> getDecoder() {
        return this::decode;
    }

    abstract protected Buffer encode(Buffer decodedBuffer);

    abstract protected Buffer decode(Buffer encodedBuffer);
}
