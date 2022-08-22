package io.github.sinri.rahab.v4a.proxy.socks5.auth.impl;

import io.github.sinri.rahab.v4a.proxy.socks5.auth.RahabSocks5AuthMethod;
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
