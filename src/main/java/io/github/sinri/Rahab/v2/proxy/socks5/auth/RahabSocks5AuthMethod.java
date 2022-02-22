package io.github.sinri.Rahab.v2.proxy.socks5.auth;

import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;


abstract public class RahabSocks5AuthMethod {
    /**
     * X'00' NO AUTHENTICATION REQUIRED
     * X'01' GSSAPI
     * X'02' USERNAME/PASSWORD
     * X'03' to X'7F' IANA ASSIGNED
     * X'80' to X'FE' RESERVED FOR PRIVATE METHODS
     * X'FF' NO ACCEPTABLE METHODS
     */
    abstract public byte getMethodByte();

    private NetSocket socketWithClient;
    private KeelLogger logger;

    public RahabSocks5AuthMethod setLogger(KeelLogger logger) {
        this.logger = logger;
        return this;
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public RahabSocks5AuthMethod setSocketWithClient(NetSocket socketWithClient) {
        this.socketWithClient = socketWithClient;
        return this;
    }

    protected NetSocket getSocketWithClient() {
        return socketWithClient;
    }

    abstract public Future<String> verifyIdentity(Buffer buffer);
}
