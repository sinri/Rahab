package io.github.sinri.Rahab.v3.consulate;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.net.NetServer;

import java.util.Date;

public class ConsulateClient extends KeelVerticle {
    private final NetServer serverAsSocks5Proxy;

    private final int port;
    private final String serverWebSocketURI;

    public ConsulateClient(int port, String serverWebSocketURI) {
        this.port = port;
        this.serverWebSocketURI = serverWebSocketURI;

        serverAsSocks5Proxy = Keel.getVertx().createNetServer();
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
                    getLogger().info("接受到客户端的socks5代理请求 from " + socketFromBrowser.remoteAddress());
                    // 接受到客户端的socks5代理请求
                    ConsulateClientHandler consulateClientHandler = new ConsulateClientHandler(serverWebSocketURI, socketFromBrowser);
                    consulateClientHandler.deployMe()
                            .onComplete(asyncResult -> {
                                if (asyncResult.failed()) {
                                    getLogger().exception("ConsulateClientHandler 部署失败", asyncResult.cause());
                                } else {
                                    getLogger().info("ConsulateClientHandlerDeploymentID: " + asyncResult.result());
                                }
                            })
                    ;
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("ConsulateClient::serverAsSocks5Proxy ERROR", throwable);
                });
        serverAsSocks5Proxy
                .listen(port)
                .onFailure(throwable -> {
                    getLogger().exception("ConsulateClient::serverAsSocks5Proxy listen failed", throwable);
                });
    }
}
