package io.github.sinri.Rahab.v3.consulate;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.NetServer;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ConsulateClient extends KeelVerticle {
    private final NetServer serverAsSocks5Proxy;
    private final HttpClient websocketClient;
    private final int port;
    //    private final String serverWebSocketURI;
    private final String wsPath;

    public ConsulateClient(int port, String wsHost, int wsPort, String wsPath) {
        this.port = port;
//        this.serverWebSocketURI = serverWebSocketURI;

        serverAsSocks5Proxy = Keel.getVertx().createNetServer();
        websocketClient = Keel.getVertx().createHttpClient(
                new HttpClientOptions()
                        .setDefaultHost(wsHost)
                        .setDefaultPort(wsPort)
                        .setIdleTimeout(10).setIdleTimeoutUnit(TimeUnit.SECONDS)
        );
        this.wsPath = wsPath;
    }

//    protected int getSocks5ProxyPort() {
//        return config().getInteger("socks5_proxy_port");
//    }
//
//    protected String getServerWSAbsoluteURI() {
//        return config().getString("server_ws_uri");
//    }

    @Override
    public void start() throws Exception {
        super.start();

        setLogger(Keel.standaloneLogger(getClass().getSimpleName()).setCategoryPrefix((new Date().getTime()) + "-" + deploymentID()));

        serverAsSocks5Proxy
                .connectHandler(socketFromBrowser -> {
                    getLogger().info("接受到用户请求 from " + socketFromBrowser.remoteAddress());
                    // 接受到客户端的socks5代理请求
                    ConsulateClientHandler consulateClientHandler = new ConsulateClientHandler(
                            wsPath,
                            socketFromBrowser,
                            websocketClient
                    );
                    consulateClientHandler.deployMe()
                            .onComplete(asyncResult -> {
                                if (asyncResult.failed()) {
                                    getLogger().exception("客户端 部署失败", asyncResult.cause());
                                } else {
                                    getLogger().info("客户端 部署 ID: " + asyncResult.result());
                                }
                            })
                    ;
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("客户端 ERROR", throwable);
                });
        serverAsSocks5Proxy
                .listen(port)
                .onFailure(throwable -> {
                    getLogger().exception("客户端 监听失败 failed", throwable);
                });
    }
}
