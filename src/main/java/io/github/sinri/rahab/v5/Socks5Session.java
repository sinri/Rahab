package io.github.sinri.rahab.v5;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Incremental RFC 1928/1929 CONNECT parser. State is never shared between clients. */
final class Socks5Session {
    private final NetSocket socket;
    private final NetClient client;
    private final String username;
    private final String password;
    private Buffer pending = Buffer.buffer();
    private int state;
    private boolean closed;
    private final long timer;
    private final Vertx vertx;

    Socks5Session(Vertx vertx, NetSocket socket, NetClient client, String username, String password) {
        this.vertx = vertx;
        this.socket = socket;
        this.client = client;
        this.username = username;
        this.password = password;
        timer = vertx.setTimer(15_000, id -> close());
        socket.closeHandler(v -> { closed = true; vertx.cancelTimer(timer); });
        socket.exceptionHandler(e -> close());
        socket.handler(this::accept);
    }

    private int u(int offset) { return pending.getUnsignedByte(offset); }
    private void consume(int count) { pending = pending.getBuffer(count, pending.length()); }
    private void close() { closed = true; state = 4; vertx.cancelTimer(timer); socket.close(); }
    private void reject(Buffer response) {
        state = 4;
        pending = Buffer.buffer();
        socket.pause();
        socket.write(response).onComplete(v -> close());
    }
    private Buffer reply(int code) {
        return Buffer.buffer(new byte[]{5, (byte) code, 0, 1, 0, 0, 0, 0, 0, 0});
    }

    private void accept(Buffer bytes) {
        if (state >= 3 || closed) return;
        pending.appendBuffer(bytes);
        // Only handshake bytes are retained; bound pipelined data while connecting.
        if (pending.length() > 1024 * 1024) { close(); return; }
        try {
            while (state < 3 && !closed) {
                if (state == 0) {
                    if (pending.length() < 2) return;
                    if (u(0) != 5 || u(1) == 0) { reject(Buffer.buffer(new byte[]{5, (byte)255})); return; }
                    int count = u(1);
                    if (pending.length() < 2 + count) return;
                    int method = username == null ? 0 : 2;
                    boolean offered = false;
                    for (int i = 0; i < count; i++) offered |= u(2 + i) == method;
                    consume(2 + count);
                    if (!offered) { reject(Buffer.buffer(new byte[]{5, (byte)255})); return; }
                    socket.write(Buffer.buffer(new byte[]{5, (byte)method})).onFailure(e -> close());
                    state = method == 0 ? 2 : 1;
                } else if (state == 1) {
                    if (pending.length() < 2) return;
                    if (u(0) != 1 || u(1) == 0) { reject(Buffer.buffer(new byte[]{1, 1})); return; }
                    int userLength = u(1);
                    if (pending.length() < 3 + userLength) return;
                    int passwordLength = u(2 + userLength);
                    if (passwordLength == 0) { reject(Buffer.buffer(new byte[]{1, 1})); return; }
                    int length = 3 + userLength + passwordLength;
                    if (pending.length() < length) return;
                    String user = pending.getString(2, 2 + userLength, "UTF-8");
                    String pass = pending.getString(3 + userLength, length, "UTF-8");
                    consume(length);
                    if (!Objects.equals(username, user) || !Objects.equals(password, pass)) {
                        reject(Buffer.buffer(new byte[]{1, 1})); return;
                    }
                    socket.write(Buffer.buffer(new byte[]{1, 0})).onFailure(e -> close());
                    state = 2;
                } else {
                    if (pending.length() < 4) return;
                    if (u(0) != 5 || u(2) != 0) { reject(reply(1)); return; }
                    if (u(1) != 1) { reject(reply(7)); return; }
                    int type = u(3), start = 4, addressLength;
                    if (type == 1) addressLength = 4;
                    else if (type == 4) addressLength = 16;
                    else if (type == 3) {
                        if (pending.length() < 5) return;
                        addressLength = u(4); start = 5;
                        if (addressLength == 0) { reject(reply(8)); return; }
                    } else { reject(reply(8)); return; }
                    int length = start + addressLength + 2;
                    if (pending.length() < length) return;
                    String host = type == 3
                            ? new String(pending.getBytes(start, start + addressLength), StandardCharsets.US_ASCII)
                            : InetAddress.getByAddress(pending.getBytes(start, start + addressLength)).getHostAddress();
                    int port = pending.getUnsignedShort(start + addressLength);
                    consume(length);
                    if (port == 0) { reject(reply(1)); return; }
                    state = 3;
                    socket.pause();
                    client.connect(port, host).onFailure(e -> reject(reply(5))).onSuccess(target -> {
                        if (closed) { target.close(); return; }
                        vertx.cancelTimer(timer);
                        byte[] address;
                        try { address = InetAddress.getByName(target.localAddress().hostAddress()).getAddress(); }
                        catch (Exception e) { target.close(); reject(reply(1)); return; }
                        Buffer success = Buffer.buffer().appendByte((byte)5).appendByte((byte)0).appendByte((byte)0)
                                .appendByte((byte)(address.length == 4 ? 1 : 4)).appendBytes(address)
                                .appendUnsignedShort(target.localAddress().port());
                        socket.write(success).onFailure(e -> { target.close(); close(); }).onSuccess(v -> {
                            TcpRelay.bridge(socket, target, pending);
                            pending = Buffer.buffer();
                        });
                    });
                }
            }
        } catch (Exception e) { close(); }
    }
}
