package io.github.sinri.Rahab.v3.consulate;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.net.NetSocket;

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;

class ConsulateClientHandler extends KeelVerticle {
    private WebSocket webSocketToServer;
    private final String absoluteURI;
    private final NetSocket socketFromBrowser;

    private final Queue<Buffer> readBufferQueue = new ArrayDeque<>();

    public ConsulateClientHandler(String absoluteURI, NetSocket socketFromBrowser) {
        this.absoluteURI = absoluteURI;
        this.socketFromBrowser = socketFromBrowser;
    }

    private synchronized void handleReadBuffers() {
        if (this.webSocketToServer == null) return;
        while (!this.readBufferQueue.isEmpty()) {
            Buffer buffer = this.readBufferQueue.remove();
            this.webSocketToServer.write(buffer);
            getLogger().info("TRANSFERRED from Browser TO Consulate Server" + buffer.toString());
        }
    }

    @Override
    public void start() throws Exception {
        super.start();
        setLogger(Keel.standaloneLogger(getClass().getSimpleName()).setCategoryPrefix((new Date().getTime()) + "-" + deploymentID()));

        this.socketFromBrowser
                .handler(buffer -> {
                    getLogger().debug("ConsulateClientHandler::socketFromBrowser RECEIVED BUFFER FROM CLIENT: " + buffer.toString());
                    // 将客户端的socks5代理包通过 ConsulateClient 转发到 ConsulateServer
                    this.readBufferQueue.add(buffer);
                    handleReadBuffers();

//                    if (this.webSocketToServer != null) {
//                        this.webSocketToServer.write(buffer);
//                    } else {
//                        getLogger().warning("webSocketToServer is null");
//                    }
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("ConsulateClient::socketFromBrowser ERROR", throwable);
                })
                .endHandler(ended -> {
                    getLogger().info("ConsulateClient::socketFromBrowser END");
                })
                .closeHandler(closed -> {
                    getLogger().info("ConsulateClient::socketFromBrowser CLOSE");
                    this.undeployMe();
                });

        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
        webSocketConnectOptions.setAbsoluteURI(this.absoluteURI);
        Keel.getVertx().createHttpClient()
                .webSocket(webSocketConnectOptions)
                .onFailure(throwable -> {
                    getLogger().exception("构建 WS CLIENT 失败", throwable);
                    this.socketFromBrowser.close()
                            .compose(v -> {
                                return this.undeployMe();
                            });
                })
                .onSuccess(webSocket -> {
                    getLogger().info("构建 WS CLIENT 成功");
                    this.webSocketToServer = webSocket;

                    this.webSocketToServer
                            .handler(buffer -> {
                                getLogger().debug("ConsulateClientHandler::webSocketToServer RECEIVED BUFFER FROM SERVER: " + buffer.toString());
                                this.socketFromBrowser.write(buffer);
                            })
                            .exceptionHandler(throwable -> {
                                getLogger().exception("ConsulateClientHandler::webSocketToServer ERROR", throwable);
                            })
                            .endHandler(ended -> {
                                getLogger().info("ConsulateClientHandler::webSocketToServer ENDED");
                            })
                            .closeHandler(closed -> {
                                getLogger().info("ConsulateClientHandler::webSocketToServer CLOSED");
                                this.undeployMe();
                            });
                    handleReadBuffers();
                });
    }
}
