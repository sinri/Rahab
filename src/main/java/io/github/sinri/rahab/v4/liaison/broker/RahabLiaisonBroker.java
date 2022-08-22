package io.github.sinri.rahab.v4.liaison.broker;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4.liaison.entity.LiaisonEnvelopeProcessor;
import io.github.sinri.rahab.v4.liaison.entity.SocketWrapper;
import io.github.sinri.rahab.v4.liaison.meta.BrokerOptions;
import io.vertx.core.net.NetServer;

import java.util.HashMap;
import java.util.Map;

public class RahabLiaisonBroker {
    private final int portForSource;
    private final int portForTerminal;
    private final NetServer serverForSource;
    private final NetServer serverForTerminal;
    private KeelLogger logger;
    private final LiaisonEnvelopeProcessor sourceEnvelopeProcessor = new LiaisonEnvelopeProcessor();

    private final Map<String, SocketWrapper> sources = new HashMap<>();
    private final Map<String, SocketWrapper> terminals = new HashMap<>();

    public RahabLiaisonBroker(BrokerOptions brokerOptions) {
        this.portForSource = brokerOptions.getProxyRegistrationPort();
        this.portForTerminal = brokerOptions.getTerminalServicePort();

        this.serverForSource = Keel.getVertx().createNetServer(
//                new NetServerOptions()
//                        .setPort(proxyRegistrationPort)
//                        .setTcpKeepAlive(true)
        );
        this.serverForTerminal = Keel.getVertx().createNetServer(
//                new NetServerOptions()
//                        .setPort(terminalServicePort)
//                        .setTcpKeepAlive(true)
        );
        this.logger = Keel.outputLogger("掮客");
    }

    public RahabLiaisonBroker setLogger(KeelLogger logger) {
        this.logger = logger;
        return this;
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void run() {
        runServerForSource();
    }

    private void runServerForSource() {
        this.serverForSource
                .connectHandler(socketFromProxy -> {
                    new RahabLiaisonBrokerHandlerForSource(this.sources, this.terminals, this.sourceEnvelopeProcessor)
                            .handle(socketFromProxy);
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("源注册服务 读取异常", throwable);
                })
                .listen(portForSource, netServerAsyncResult -> {
                    if (netServerAsyncResult.failed()) {
                        getLogger().exception("源注册服务 LISTEN FAILED", netServerAsyncResult.cause());
                        Keel.getVertx().close();
                        return;
                    }

                    getLogger().info("源注册服务 LISTENING on port " + portForSource);

                    runTerminalServiceServer();
                });
    }

    private void runTerminalServiceServer() {
        this.serverForTerminal
                .connectHandler(socket -> {
                    new RahabLiaisonBrokerHandlerForTerminal(sources, terminals)
                            .handle(socket);
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("终端服务 读取异常", throwable);
                })
                .listen(portForTerminal, netServerAsyncResult -> {
                    if (netServerAsyncResult.failed()) {
                        getLogger().exception("终端服务 LISTEN FAILED", netServerAsyncResult.cause());
                        Keel.getVertx().close();
                        return;
                    }

                    getLogger().info("终端服务 LISTENING on port " + portForTerminal);
                });
    }
}
