package io.github.sinri.Rahab.v3.wormhole.transform.impl.http.client;

import io.github.sinri.Rahab.v3.wormhole.transform.WormholeTransformer;
import io.vertx.core.buffer.Buffer;

public class TransformerFromHttpRequestToRaw extends WormholeTransformer {

    @Override
    protected Buffer transformOnce(Buffer inputBuffer) {
//        logger.debug("transform start with historic unhandledBuffer length is "+unhandledBuffer.length());
        unhandledBuffer.appendBuffer(inputBuffer);
//        logger.debug("input buffer length is "+inputBuffer.length());

        int since = -1;
        for (int i = 0; i < unhandledBuffer.length() - 4; i++) {
            if (unhandledBuffer.getByte(i) != '\r') {
                continue;
            }
            if (
                    unhandledBuffer.getByte(i + 1) == '\n'
                            && unhandledBuffer.getByte(i + 2) == '\r'
                            && unhandledBuffer.getByte(i + 3) == '\n'
            ) {
                since = i + 4;
                break;
            }
        }

//        logger.debug("seek TWO NEW LINES, since found is "+since);

        Buffer buffer = Buffer.buffer();

        if (since < 0) {
            logger.warning("not found TWO NEW LINES: since<0");
            return buffer;
        }

        if (since + 4 > unhandledBuffer.length()) {
            logger.warning("BUFFER NOT ENTIRE: since+4>unhandledBuffer.length()");
            return buffer;
        }
        int realBufferLength = unhandledBuffer.getInt(since);
        since += 4;
//        logger.debug("realBufferLength is "+realBufferLength+" now since becomes "+since);

        if (since + realBufferLength > unhandledBuffer.length()) {
            logger.warning("Real Buffer is not entire, rest bytes: " + (unhandledBuffer.length() - since - realBufferLength));
            return buffer;
        }

        byte delta2 = 0;
        for (int i = 0; i < realBufferLength; i++) {
            byte x = unhandledBuffer.getByte(since + i);
            delta2 = (byte) (x - delta2);
            buffer.appendByte(delta2);
            delta2 = x;
        }

        unhandledBuffer = Buffer.buffer(unhandledBuffer.getBytes(since + realBufferLength, unhandledBuffer.length()));
        return buffer;
    }
}
