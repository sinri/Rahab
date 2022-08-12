package io.github.sinri.rahab.v4.wormhole.liaison;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.net.NetServer;

import java.util.Date;
import java.util.UUID;

/**
 * 情报掮客
 * 面向两类客户端
 * 1. 情报源: RahabLiaisonSource
 * 2. 情报客户: 任意一个TCP客户端即可
 *
 * @since 3.0.0
 */
public class RahabLiaisonBrokerVerticle extends KeelVerticle {
    /**
     * 掮客服务器
     */
    private final NetServer brokerServer;
    private final ClientSocketManager clientSocketManager;
    private final int port;

    public RahabLiaisonBrokerVerticle(int port) {
        this.brokerServer = Keel.getVertx().createNetServer();
        this.port = port;
        this.clientSocketManager = new ClientSocketManager();
    }

    private KeelLogger sourceRegistrationLogger;

    @Override
    public void start() throws Exception {
        setLogger(Keel.outputLogger("RahabProxyBroker"));

        this.brokerServer
                .connectHandler(socket -> {
                    String requestID = new Date().getTime() + "-" + UUID.randomUUID().toString().replace("-", "");

                    new RahabLiaisonBrokerWorkerVerticle(socket, requestID, clientSocketManager)
                            .deployMe();
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("掮客服务器 出错", throwable);
                    undeployMe();
                })
                .listen(port);
    }

}
