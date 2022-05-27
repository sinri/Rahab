package io.github.sinri.Rahab.v3.consulate;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import io.vertx.core.net.NetSocket;

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;

class ConsulateClientHandler extends KeelVerticle {
    private WebSocket webSocketToServer;
    //    private final String absoluteURI;
    private final String wsPath;
    private final NetSocket socketFromBrowser;
    private final HttpClient websocketClient;

    private final Queue<Buffer> readBufferQueue = new ArrayDeque<>();

    public ConsulateClientHandler(String wsPath, NetSocket socketFromBrowser, HttpClient websocketClient) {
//        this.absoluteURI = absoluteURI;
        this.wsPath = wsPath;
        this.socketFromBrowser = socketFromBrowser;
        this.websocketClient = websocketClient;
    }

    private synchronized void handleReadBuffers() {
        getLogger().debug("准备对待处理消息队列下手　LEN: " + this.readBufferQueue.size());
        if (this.webSocketToServer == null) {
            getLogger().debug("与 ws 服务器 的 通信 还没有建立");
            setupWebSocketToServer();
            return;
        }
        while (!this.readBufferQueue.isEmpty()) {
            Buffer buffer = this.readBufferQueue.remove();
            this.webSocketToServer.write(buffer);
            getLogger().info("通过 ws 转发 用户请求 " + buffer.length() + " bytes 到 ws 服务器");
            getLogger().print(KeelLogLevel.DEBUG, buffer.toString());
//            getLogger().debug(KeelHelper.bufferToHexMatrix(buffer, 40));
        }
    }

    @Override
    public void start() throws Exception {
        super.start();
        setLogger(Keel.standaloneLogger(getClass().getSimpleName()).setCategoryPrefix((new Date().getTime()) + "-" + deploymentID()));

        getLogger().info("客户端服务专线 START");
        this.socketFromBrowser
                .handler(buffer -> {
                    getLogger().info("客户端服务专线 接收到 用户请求 " + buffer.length() + " bytes 加入待处理消息队列");
                    getLogger().print(KeelLogLevel.DEBUG, buffer.toString());
                    //getLogger().debug(KeelHelper.bufferToHexMatrix(buffer,40));
                    // 将客户端的socks5代理包通过 ConsulateClient 转发到 ConsulateServer
                    this.readBufferQueue.add(buffer);
                    handleReadBuffers();
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("客户端服务专线 ERROR", throwable);
                })
                .endHandler(ended -> {
                    getLogger().info("客户端服务专线 END");
                })
                .closeHandler(closed -> {
                    getLogger().info("客户端服务专线 CLOSE");
                    if (this.webSocketToServer != null) {
                        this.webSocketToServer.close().compose(v -> this.undeployMe());
                    } else {
                        this.undeployMe();
                    }
                });
    }

    private void setupWebSocketToServer() {
        getLogger().debug("建立 与 ws 服务器 的 通信 ...");
        //WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
        //webSocketConnectOptions.setAbsoluteURI(this.absoluteURI);
        this.websocketClient
                .webSocket(wsPath)
                //.webSocket(webSocketConnectOptions)
                .onFailure(throwable -> {
                    getLogger().exception("建立 与 ws 服务器 的 通信 失败", throwable);
                    this.socketFromBrowser.close()
                            .compose(v -> {
                                return this.undeployMe();
                            });
                })
                .onSuccess(webSocket -> {
                    getLogger().info("建立 与 ws 服务器 的 通信 成功");
                    this.webSocketToServer = webSocket;

                    this.webSocketToServer
                            .handler(buffer -> {
                                getLogger().info("接受 自 ws 服务器 " + buffer.length() + " bytes");
                                getLogger().print(KeelLogLevel.DEBUG, buffer.toString());
//                                getLogger().debug(KeelHelper.bufferToHexMatrix(buffer,40));
                                this.socketFromBrowser.write(buffer);
                            })
                            .exceptionHandler(throwable -> {
                                getLogger().exception("与 ws 服务器 的 通信 ERROR", throwable);
                            })
                            .endHandler(ended -> {
                                getLogger().info("与 ws 服务器 的 通信 ENDED");
                            })
                            .closeHandler(closed -> {
                                getLogger().info("与 ws 服务器 的 通信 CLOSED");
                                if (this.socketFromBrowser != null) {
                                    this.socketFromBrowser.close().compose(v -> this.undeployMe());
                                } else {
                                    this.undeployMe();
                                }
                            });
                    handleReadBuffers();
                });
    }
}
