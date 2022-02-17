package io.github.sinri.Rahab.v2.transform.impl.http.client;

import io.github.sinri.Rahab.v2.transform.WormholeTransformer;
import io.vertx.core.buffer.Buffer;

import java.util.UUID;

public class TransformerFromRawToHttpRequest extends WormholeTransformer {
    private final String fakeHost;

    public TransformerFromRawToHttpRequest(String fakeHost) {
        this.fakeHost = fakeHost;
    }

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
        buffer.appendString("POST /api/generateMockDataBlock?hash=" + x + " HTTP/1.1\r\n")
                .appendString("Host: " + fakeHost + "\r\n")
                .appendString("User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.80 Safari/537.36" + "\r\n")
                .appendString("Connection: keep-alive" + "\r\n")
                .appendString("Content-Length: " + bodyBuffer.length() + "\r\n")
                .appendString("Content-Type: text/plain\r\n")
                .appendString("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\r\n")
                .appendString("Accept-Encoding: gzip, deflate, br\r\n")
                .appendString("Accept-Language: en-US,en;q=0.9\r\n")
                .appendString("Cookie: API-TOKEN=" + y + "\r\n")
                .appendString("\r\n")
                .appendBuffer(bodyBuffer);

        return buffer;
    }
}
