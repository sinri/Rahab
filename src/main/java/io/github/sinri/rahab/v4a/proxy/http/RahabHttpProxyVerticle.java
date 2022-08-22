package io.github.sinri.rahab.v4a.proxy.http;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.Future;
import io.vertx.core.net.NetServer;

import java.util.Date;
import java.util.UUID;

/**
 * HTTP Proxy
 * {浏览器} ←[代理通讯]→ {代理服务器 ↔ 中介客户端} ←[自由通讯]→ {目标服务器}
 *
 * @since 3.0.0
 */
public class RahabHttpProxyVerticle extends KeelVerticle {
    private final int proxyPort;
    private final NetServer proxyServer;
    private final KeelLogger httpProxyLogger;

    private boolean runAsUniqueDaemon = true;

    public RahabHttpProxyVerticle(int proxyPort) {
        this.proxyPort = proxyPort;
        this.proxyServer = Keel.getVertx().createNetServer();
        this.httpProxyLogger = Keel.outputLogger("RahabHttpProxyVerticle");
    }

    public RahabHttpProxyVerticle setRunAsUniqueDaemon(boolean runAsUniqueDaemon) {
        this.runAsUniqueDaemon = runAsUniqueDaemon;
        return this;
    }

    @Override
    public void start() throws Exception {
        super.start();

        this.setLogger(this.httpProxyLogger);

        this.proxyServer
                .connectHandler(proxySocket -> {
                    String requestID = new Date().getTime() + "-" + UUID.randomUUID().toString().replace("-", "");
                    getLogger().info("[" + requestID + "]" + " Incoming Socket from remote [" + proxySocket.remoteAddress().toString() + "] to local [" + proxySocket.localAddress().toString() + "]");
                    RahabHttpProxyWorkerVerticle rahabHttpProxyWorkerVerticle = new RahabHttpProxyWorkerVerticle(proxySocket, requestID);
                    rahabHttpProxyWorkerVerticle.deployMe()
                            .compose(deploymentID -> {
                                getLogger().info("[" + requestID + "]" + " RahabHttpProxyWorkerVerticle deployed an instance " + deploymentID);
                                return Future.succeededFuture();
                            });
                })
                .exceptionHandler(throwable -> {
                    this.httpProxyLogger.exception("代理服务器 出现故障", throwable);
                })
                .listen(proxyPort)
                .onSuccess(server -> {
                    getLogger().notice("RahabHttpProxy 开始运作 端口为 " + proxyPort);
                    // undeploy it when vertx closed
                })
                .onFailure(throwable -> {
                    getLogger().exception("RahabHttpProxy 启动失败 端口为 " + proxyPort, throwable);
                    if (runAsUniqueDaemon) {
                        Keel.getVertx().close();
                    } else {
                        undeployMe();
                    }
                });
    }
}
