package io.github.sinri.Rahab.v2.proxy.socks5;

import io.github.sinri.Rahab.v2.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.github.sinri.keel.Keel;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.Future;
import io.vertx.core.net.NetServer;

import java.util.*;

/**
 * @since 3.0.0
 */
public class RahabSocks5ProxyVerticle extends KeelVerticle {
    /**
     * SOCKS5 代理服务器
     */
    protected final NetServer socks5Server;

    private final int proxyPort;

    private boolean runAsUniqueDaemon = true;

    /**
     * 本代理支持的鉴权方法
     */
    protected Map<Byte, RahabSocks5AuthMethod> supportedAuthMethodMap;

    public RahabSocks5ProxyVerticle(int proxyPort, Set<RahabSocks5AuthMethod> supportedAuthMethodSet) {
        this.proxyPort = proxyPort;
        this.socks5Server = Keel.getVertx().createNetServer();

        this.supportedAuthMethodMap = new HashMap<>();
        supportedAuthMethodSet.forEach(rahabSocks5AuthMethod -> this.supportedAuthMethodMap.put(rahabSocks5AuthMethod.getMethodByte(), rahabSocks5AuthMethod));
    }

    public RahabSocks5ProxyVerticle setRunAsUniqueDaemon(boolean runAsUniqueDaemon) {
        this.runAsUniqueDaemon = runAsUniqueDaemon;
        return this;
    }

    @Override
    public void start() throws Exception {
        setLogger(Keel.outputLogger("RahabSocks5ProxyVerticle"));

        this.socks5Server
                .connectHandler(socketFromClient -> {
                    String requestID = new Date().getTime() + "-" + UUID.randomUUID().toString().replace("-", "");
                    getLogger().info("[" + requestID + "]" + " Incoming Socket from remote [" + socketFromClient.remoteAddress().toString() + "] to local [" + socketFromClient.localAddress().toString() + "]");

                    RahabSocks5ProxyWorkerVerticle rahabSocks5ProxyWorkerVerticle = new RahabSocks5ProxyWorkerVerticle(socketFromClient, requestID, supportedAuthMethodMap);
                    rahabSocks5ProxyWorkerVerticle.deployMe()
                            .compose(deploymentID -> {
                                getLogger().info("[" + requestID + "]" + " RahabSocks5ProxyWorkerVerticle deployed an instance " + deploymentID);
                                return Future.succeededFuture();
                            });
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("RahabSocks5Proxy Error", throwable);
                })
                .listen(proxyPort)
                .onSuccess(server -> {
                    getLogger().notice("RahabSocks5Proxy 开始运作 端口为 " + proxyPort);
                    // undeploy it when vertx closed
                })
                .onFailure(throwable -> {
                    getLogger().exception("RahabSocks5Proxy 启动失败 端口为 " + proxyPort, throwable);
                    if (runAsUniqueDaemon) {
                        Keel.getVertx().close();
                    }
                });
    }
}
