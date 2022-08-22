package io.github.sinri.rahab.v4.liaison.entity;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class LiaisonEnvelope {
    Buffer contentBuffer;
    String contentHolder;

    public LiaisonEnvelope(Buffer contentBuffer, String contentHolder) {
        this.contentHolder = contentHolder;
        this.contentBuffer = contentBuffer;
    }

    public Buffer getContentBuffer() {
        return contentBuffer;
    }

    public String getContentHolder() {
        return contentHolder;
    }

    public Buffer toBuffer() {
        Buffer bufferClientName = Buffer.buffer(contentHolder);
        return Buffer.buffer()
                .appendInt(4 + 4 + bufferClientName.length() + 4 + contentBuffer.length()) // 4
                .appendInt(bufferClientName.length()) // 4
                .appendBuffer(bufferClientName) // x
                .appendInt(contentBuffer.length()) // 4
                .appendBuffer(contentBuffer); // y
    }

    public static LiaisonEnvelope buildForSourceToRegister(String sourceName) {
        return new LiaisonEnvelope(Buffer.buffer(), "R" + sourceName);
    }

    @Deprecated
    public static LiaisonEnvelope buildForSourceToRegister(String sourceName, String username, String password) {
        return new LiaisonEnvelope(
                new JsonObject()
                        .put("username", username)
                        .put("password", password)
                        .toBuffer(),
                "R" + sourceName
        );
    }

    public static LiaisonEnvelope buildForSourceToTransfer(String terminalName, Buffer buffer) {
        return new LiaisonEnvelope(
                buffer,
                "T" + terminalName
        );
    }

    public static LiaisonEnvelope buildForSourceToUnregister(String sourceName) {
        return new LiaisonEnvelope(
                Buffer.buffer(),
                "U" + sourceName
        );
    }

    public static LiaisonEnvelope buildForBrokerToTransfer(String terminalName, Buffer buffer) {
        return new LiaisonEnvelope(
                buffer,
                terminalName
        );
    }
}
