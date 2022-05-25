package io.github.sinri.Rahab.v3.proxy.socks5.auth.impl;

import io.github.sinri.Rahab.v3.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

public class RahabSocks5AuthMethod02 extends RahabSocks5AuthMethod {

    private final UsernamePasswordVerifier usernamePasswordVerifier;

    public RahabSocks5AuthMethod02(UsernamePasswordVerifier usernamePasswordVerifier) {
        this.usernamePasswordVerifier = usernamePasswordVerifier;
    }

    @Override
    public byte getMethodByte() {
        return 0x02;
    }

    @Override
    public Future<String> verifyIdentity(Buffer buffer) {
        /*
        +----+------+----------+------+----------+
        |VER | ULEN |  UNAME   | PLEN |  PASSWD  |
        +----+------+----------+------+----------+
        | 1  |  1   | 1 to 255 |  1   | 1 to 255 |
        +----+------+----------+------+----------+
         */

        int ptr = 0;
        byte version = buffer.getByte(ptr);// 0x01
        ptr += 1;
        byte usernameLength = buffer.getByte(ptr);
        ptr += 1;
        byte[] username = buffer.getBytes(ptr, ptr + usernameLength);
        ptr += usernameLength;
        byte passwordLength = buffer.getByte(ptr);
        ptr += 1;
        byte[] password = buffer.getBytes(ptr, ptr + passwordLength);
        ptr += passwordLength;

        String u = new String(username);
        String p = new String(password);
        return this.usernamePasswordVerifier.verify(u, p)
                .compose(aBoolean -> this.getSocketWithClient()
                        .write(Buffer.buffer()
                                .appendByte((byte) 0x01)
                                .appendByte(aBoolean ? (byte) 0x00 : (byte) 0x01)
                        )
                        .onComplete(voidAsyncResult -> {
                            if (voidAsyncResult.failed()) {
                                getLogger().exception("writing failed", voidAsyncResult.cause());
                                getLogger().error("TO CLOSE CLIENT SOCKET");
                                getSocketWithClient().close();
                            } else {
                                getLogger().info("WRITING DONE");
                            }
                        })
                        .compose(v -> Future.succeededFuture(u)));
    }

    abstract public static class UsernamePasswordVerifier {
        abstract public Future<Boolean> verify(String username, String password);
    }
}
