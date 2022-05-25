package io.github.sinri.Rahab.v3.wormhole.transform.impl.http.server;

import io.github.sinri.Rahab.v3.wormhole.transform.WormholeTransformer;
import io.vertx.core.buffer.Buffer;

import java.time.LocalDateTime;
import java.util.UUID;

public class TransformerFromRawToHttpResponse extends WormholeTransformer {
    @Override
    protected Buffer transformOnce(Buffer inputBuffer) {
        if (inputBuffer.length() <= 0) {
            return Buffer.buffer();
        }

        String x = UUID.randomUUID().toString();
        String y = UUID.randomUUID().toString().replace("-", "").toUpperCase();

        // byte = [-128,127]

        int realBufferLength = inputBuffer.length();

        Buffer bodyBuffer = Buffer.buffer();
        bodyBuffer.appendInt(realBufferLength);
        byte delta = 0;
        for (var i = 0; i < inputBuffer.length(); i++) {
            byte b = inputBuffer.getByte(i);
            byte encodedByte = (byte) (b + delta);
            delta = encodedByte;
            bodyBuffer.appendByte(encodedByte);
        }

        Buffer buffer = Buffer.buffer();
        buffer.appendString("HTTP/1.1 200 OK\r\n")
                .appendString("Date: " + (LocalDateTime.now()) + "\r\n")
                .appendString("Connection: keep-alive" + "\r\n")
                .appendString("Content-Length: " + bodyBuffer.length() + "\r\n")
                .appendString("Content-Type: text/plain\r\n")
                .appendString("\r\n")
                .appendBuffer(bodyBuffer);

        return buffer;
    }
}
