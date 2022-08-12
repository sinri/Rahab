package io.github.sinri.rahab.v4.wormhole;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.github.sinri.rahab.v4.wormhole.transform.WormholeTransformer;
import io.vertx.core.Future;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;

import java.util.Date;
import java.util.UUID;

/**
 * @since 3.0.0
 */
public class WormholeVerticle extends KeelVerticle {
    private final String wormholeName;
    private final String destinationHost;
    private final int destinationPort;

    /**
     * 近端服务器
     */
    protected NetServer localServer;
    /**
     * 远端客户端
     */
    protected NetClient remoteClient;

    private WormholeTransformer transformerForDataFromLocal;
    private WormholeTransformer transformerForDataFromRemote;

    private final int listenPort;
    private boolean runAsUniqueDaemon = true;

    public WormholeVerticle(String wormholeName, int listenPort, String destinationHost, int destinationPort) {
        this.wormholeName = wormholeName;
        this.destinationPort = destinationPort;
        this.destinationHost = destinationHost;
        localServer = Keel.getVertx().createNetServer();
        this.transformerForDataFromLocal = null;
        this.transformerForDataFromRemote = null;
        this.remoteClient = Keel.getVertx().createNetClient();
        this.listenPort = listenPort;
    }

    /**
     * 加密中继模式中用于处理来自近端通讯的数据
     *
     * @param transformerForDataFromLocal WormholeTransformer
     * @return this
     */
    public WormholeVerticle setTransformerForDataFromLocal(WormholeTransformer transformerForDataFromLocal) {
        this.transformerForDataFromLocal = transformerForDataFromLocal;
        return this;
    }

    /**
     * 加密中继模式中用于处理来自远端通讯的数据
     *
     * @param transformerForDataFromRemote WormholeTransformer
     * @return this
     */
    public WormholeVerticle setTransformerForDataFromRemote(WormholeTransformer transformerForDataFromRemote) {
        this.transformerForDataFromRemote = transformerForDataFromRemote;
        return this;
    }

    public WormholeVerticle setRunAsUniqueDaemon(boolean runAsUniqueDaemon) {
        this.runAsUniqueDaemon = runAsUniqueDaemon;
        return this;
    }

    @Override
    public void start() throws Exception {
        setLogger(Keel.outputLogger("WormholeProxy"));

        localServer
                .connectHandler(localSocket -> {
                    String requestID = new Date().getTime() + "-" + UUID.randomUUID().toString().replace("-", "");

                    WormholeWorkerVerticle wormholeWorkerVerticle = new WormholeWorkerVerticle(
                            localSocket,
                            requestID,
                            wormholeName,
                            destinationHost,
                            destinationPort,
                            remoteClient
                    )
                            .setTransformerForDataFromLocal(transformerForDataFromLocal)
                            .setTransformerForDataFromRemote(transformerForDataFromRemote);
                    wormholeWorkerVerticle.deployMe()
                            .compose(deploymentID -> {
                                getLogger().info("[" + requestID + "]" + " WormholeWorkerVerticle deployed an instance " + deploymentID);
                                return Future.succeededFuture();
                            });
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("近端服务器 出错, 即将关闭", throwable);
                    remoteClient.close();
                    localServer.close();
                })
                .listen(listenPort)
                .onSuccess(server -> {
                    getLogger().notice("WormholeVerticle 开始运作 端口为 " + listenPort);
                    // undeploy it when vertx closed
                })
                .onFailure(throwable -> {
                    getLogger().exception("WormholeVerticle 启动失败 端口为 " + listenPort, throwable);
                    if (runAsUniqueDaemon) {
                        Keel.getVertx().close();
                    }
                });
    }


}
