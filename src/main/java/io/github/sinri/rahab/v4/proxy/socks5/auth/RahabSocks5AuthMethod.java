package io.github.sinri.rahab.v4.proxy.socks5.auth;

import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.web.tcp.KeelAbstractSocketWrapper;
import io.github.sinri.rahab.v4.proxy.socks5.auth.impl.RahabSocks5AuthMethod00;
import io.github.sinri.rahab.v4.proxy.socks5.auth.impl.RahabSocks5AuthMethod02;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

import java.util.HashMap;
import java.util.Map;


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

    private KeelAbstractSocketWrapper socketWrapperWithClient;
    private KeelLogger logger;

    public RahabSocks5AuthMethod setLogger(KeelLogger logger) {
        this.logger = logger;
        return this;
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public RahabSocks5AuthMethod setSocketWithClient(KeelAbstractSocketWrapper socketWrapperWithClient) {
        this.socketWrapperWithClient = socketWrapperWithClient;
        return this;
    }

    public KeelAbstractSocketWrapper getSocketWrapperWithClient() {
        return socketWrapperWithClient;
    }

    abstract public Future<String> verifyIdentity(Buffer buffer);

    public static Map<Byte, RahabSocks5AuthMethod> createAnonymousMap() {
        Map<Byte, RahabSocks5AuthMethod> map = new HashMap<>();
        map.put((byte) 0, new RahabSocks5AuthMethod00());
        return map;
    }

    public static Map<Byte, RahabSocks5AuthMethod> createBasicAuthMap(
            RahabSocks5AuthMethod02.UsernamePasswordVerifier usernamePasswordVerifier
    ) {
        Map<Byte, RahabSocks5AuthMethod> map = new HashMap<>();
        map.put((byte) 2, new RahabSocks5AuthMethod02(usernamePasswordVerifier));
        return map;
    }
}
