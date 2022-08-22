package io.github.sinri.rahab.v4.liaison.entity;

import io.vertx.core.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;

public class LiaisonEnvelopeProcessor {

    Buffer unhandledBuffer = Buffer.buffer();

    public static Buffer makeNamedDataBuffer(Buffer contentBufferFromClientToProxy, String clientID) {
        return new LiaisonEnvelope(contentBufferFromClientToProxy, clientID).toBuffer();
    }

    protected synchronized LiaisonEnvelope parseNextOne() {
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

        return new LiaisonEnvelope(rawBufferFromClientToProxy, bufferClientName);
    }

    public List<LiaisonEnvelope> parseAll(Buffer incomingBuffer) {
        unhandledBuffer.appendBuffer(incomingBuffer);

        List<LiaisonEnvelope> list = new ArrayList<>();
        while (true) {
            LiaisonEnvelope liaisonEnvelope = parseNextOne();
            if (liaisonEnvelope == null) break;
            list.add(liaisonEnvelope);
        }
        return list;
    }

}
