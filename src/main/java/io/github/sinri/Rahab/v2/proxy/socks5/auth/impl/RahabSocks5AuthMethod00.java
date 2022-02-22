package io.github.sinri.Rahab.v2.proxy.socks5.auth.impl;

import io.github.sinri.Rahab.v2.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

public class RahabSocks5AuthMethod00 extends RahabSocks5AuthMethod {
    @Override
    public byte getMethodByte() {
        return 0;
    }

    @Override
    public Future<String> verifyIdentity(Buffer buffer) {
        return Future.succeededFuture("Anonymous");
    }
}
