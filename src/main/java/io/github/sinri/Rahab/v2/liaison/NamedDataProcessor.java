package io.github.sinri.Rahab.v2.liaison;

import io.vertx.core.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;

public class NamedDataProcessor {

    Buffer unhandledBuffer = Buffer.buffer();

    public static Buffer makeNamedDataBuffer(Buffer rawBufferFromClientToProxy, String clientID) {
        return new NamedData(rawBufferFromClientToProxy, clientID).toBuffer();
    }

    protected NamedData parseNextOne() {
        if (unhandledBuffer.length() < 4) {
            return null;
        }

        // seek for next
        int indexOfNextUnreadByte = 0;

        int entireLength = unhandledBuffer.getInt(indexOfNextUnreadByte);
        indexOfNextUnreadByte += 4;
        if (unhandledBuffer.length() < entireLength) {
            return null;
        }

        int bufferClientNameLength = unhandledBuffer.getInt(indexOfNextUnreadByte);
        indexOfNextUnreadByte += 4;

        String bufferClientName = unhandledBuffer.getString(indexOfNextUnreadByte, indexOfNextUnreadByte + bufferClientNameLength);
        indexOfNextUnreadByte += bufferClientNameLength;

        int rawBufferFromClientToProxyLength = unhandledBuffer.getInt(indexOfNextUnreadByte);
        indexOfNextUnreadByte += 4;

        Buffer rawBufferFromClientToProxy = unhandledBuffer.getBuffer(indexOfNextUnreadByte, indexOfNextUnreadByte + rawBufferFromClientToProxyLength);
        indexOfNextUnreadByte += rawBufferFromClientToProxyLength;

        // cut off done part in unhandledBuffer
        unhandledBuffer = Buffer.buffer(unhandledBuffer.getBytes(indexOfNextUnreadByte, unhandledBuffer.length()));

        return new NamedData(rawBufferFromClientToProxy, bufferClientName);
    }

    public List<NamedData> parseAll(Buffer incomingBuffer) {
        unhandledBuffer.appendBuffer(incomingBuffer);

        List<NamedData> list = new ArrayList<>();
        while (true) {
            NamedData namedData = parseNextOne();
            if (namedData == null) break;
            list.add(namedData);
        }
        return list;
    }

    public static class NamedData {
        Buffer rawBuffer;
        String clientID;

        public NamedData(Buffer rawBuffer, String clientID) {
            this.clientID = clientID;
            this.rawBuffer = rawBuffer;
        }

        public Buffer getRawBuffer() {
            return rawBuffer;
        }

        public String getClientID() {
            return clientID;
        }

        public Buffer toBuffer() {
            Buffer bufferClientName = Buffer.buffer(clientID);
            return Buffer.buffer()
                    .appendInt(4 + 4 + bufferClientName.length() + 4 + rawBuffer.length()) // 4
                    .appendInt(bufferClientName.length()) // 4
                    .appendBuffer(bufferClientName) // x
                    .appendInt(rawBuffer.length()) // 4
                    .appendBuffer(rawBuffer); // y
        }
    }
}
