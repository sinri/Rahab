package io.github.sinri.Rahab.v2.transform;

import io.vertx.core.buffer.Buffer;

import java.util.UUID;

public class HttpRequestTransformPair extends WormholeTransformPair {

    private final String fakeHost;

    public HttpRequestTransformPair(String fakeHost) {
        this.fakeHost = fakeHost;
    }

    @Override
    protected Buffer encode(Buffer decodedBuffer) {
        String x = UUID.randomUUID().toString();
        String y = UUID.randomUUID().toString().replace("-", "").toUpperCase();

        // byte = [-128,127]

        Buffer bodyBuffer = Buffer.buffer();
        byte delta = 0;
        for (var i = 0; i < decodedBuffer.length(); i++) {
            byte b = decodedBuffer.getByte(i);
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
                .appendString("Content-Type: text/plain")
                .appendString("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\r\n")
                .appendString("Accept-Encoding: gzip, deflate, br\r\n")
                .appendString("Accept-Language: en-US,en;q=0.9\r\n")
                .appendString("Cookie: API-TOKEN=" + y + "\r\n")
                .appendString("\r\n")
                .appendBuffer(bodyBuffer);

        return buffer;
    }

    @Override
    protected Buffer decode(Buffer encodedBuffer) {
        int since = 0;
        for (int i = 0; i < encodedBuffer.length() - 4; i++) {
            if (encodedBuffer.getByte(i) != '\r') {
                continue;
            }
            if (
                    encodedBuffer.getByte(i + 1) == '\n'
                            && encodedBuffer.getByte(i + 2) == '\r'
                            && encodedBuffer.getByte(i + 3) == '\n'
            ) {
                since = i + 4;
                break;
            }
        }

        Buffer buffer = Buffer.buffer();

        byte delta2=0;
        for(int i=0;i<encodedBuffer.length()-since;i++){
            byte x=encodedBuffer.getByte(since+i);
            delta2=(byte)(x-delta2);
            buffer.appendByte(delta2);
            delta2=x;
        }

        return buffer;
    }

    public static void main(String[] args) {
        HttpRequestTransformPair httpRequestTransformPair = new HttpRequestTransformPair("fake.com");

        Buffer rawBuffer = Buffer.buffer();
        rawBuffer.appendString("Let it go!");

        Buffer encoded = httpRequestTransformPair.encode(rawBuffer);
        Buffer decoded = httpRequestTransformPair.decode(encoded);
        System.out.println(decoded);

         encoded = httpRequestTransformPair.decode(rawBuffer);
         decoded = httpRequestTransformPair.encode(encoded);
        System.out.println(decoded);
    }
}
