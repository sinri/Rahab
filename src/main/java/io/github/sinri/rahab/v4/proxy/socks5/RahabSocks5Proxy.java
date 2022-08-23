package io.github.sinri.rahab.v4.proxy.socks5;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;

import java.util.Map;
import java.util.Objects;

public class RahabSocks5Proxy {
    private final int listenPort;
    private final NetServer socks5Server;
    private KeelLogger logger;
    private final Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap;
    private final NetClient clientToActualServer;

    public RahabSocks5Proxy(int listenPort) {
        this(listenPort, RahabSocks5AuthMethod.createAnonymousMap());
    }

    public RahabSocks5Proxy(int listenPort, Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap) {
        this.listenPort = listenPort;
        this.socks5Server = Keel.getVertx().createNetServer(
                new NetServerOptions()
                        .setPort(listenPort)
        );
        this.logger = Keel.standaloneLogger("RahabSocks5Proxy");
        this.supportedAuthMethodMap = supportedAuthMethodMap;
        this.clientToActualServer = Keel.getVertx().createNetClient();
    }

    public RahabSocks5Proxy setLogger(KeelLogger logger) {
        this.logger = Objects.requireNonNullElse(logger, KeelLogger.silentLogger());
        return this;
    }

    protected KeelLogger getLogger() {
        return this.logger;
    }

    public void run() {
        getLogger().info("READY, PORT " + this.listenPort);
        this.socks5Server
                .connectHandler(socket -> TerminalSocketWrapper.handle(
                        socket,
                        this.supportedAuthMethodMap,
                        this.clientToActualServer
                ))
                .exceptionHandler(throwable -> {
                    // Set an exception handler called for socket errors happening before the connection
                    //  is passed to the connectHandler(io.vertx.core.Handler<io.vertx.core.net.NetSocket>),
                    //  e.g during the TLS handshake.
                    getLogger().exception("RahabSocks5Proxy Exception", throwable);
                })
                .listen(listenAsyncResult -> {
                    if (listenAsyncResult.failed()) {
                        getLogger().exception("LISTEN FAILED", listenAsyncResult.cause());
                        Keel.getVertx().close();
                    } else {
                        getLogger().info("KEEP LISTENING");
                    }
                });
    }

}
