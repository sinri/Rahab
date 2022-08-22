package io.github.sinri.rahab.v4a.consulate;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.web.websockets.KeelWebSocketHandler;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;

public class ConsulateServerHandler extends KeelWebSocketHandler {

    private final NetClient netClient;
    private NetSocket socketToTargetServer;

    private final Queue<Buffer> readBufferQueue = new ArrayDeque<>();

    public ConsulateServerHandler(ServerWebSocket webSocket) {
        super(webSocket);

        netClient = Keel.getVertx().createNetClient();
    }

    protected String getSocks5Host() {
        return config().getString("socks5_host");
        //return "127.0.0.1";
    }

    protected int getSocks5Port() {
        return config().getInteger("socks5_port");
        //return 10000;
    }

    protected String getWebsocketPath() {
        return config().getString("ws_path");
    }

    @Override
    protected KeelLogger prepareLogger() {
        return Keel.standaloneLogger(getClass().getSimpleName()).setCategoryPrefix((new Date().getTime()) + "-" + deploymentID());
    }

    @Override
    protected Future<Boolean> shouldReject() {
        if (!this.getWebSocketPath().equals(getWebsocketPath())) {
            return Future.succeededFuture(true);
        }
        return Future.succeededFuture(false);
    }

    @Override
    protected void accept() {
        // accept a new ws connection from client
        getLogger().info("接受 新的 客户端专线  FROM " + this.getWebSocketRemoteAddress() + " TO LOCAL " + this.getWebSocketLocalAddress());

        // this.writeText("ACCEPTED");
        netClient.connect(getSocks5Port(), getSocks5Host())
                .onSuccess(socket -> {
                    getLogger().info("为此 客户端专线 建立与 SOCKS5 服务器的 代理专线");
                    this.socketToTargetServer = socket;
                    this.socketToTargetServer
                            .handler(buffer -> {
                                // from actual server
                                getLogger().info("代理专线 接受到 来自目标的报文 " + buffer.length() + " bytes");
                                getLogger().text(KeelLogLevel.DEBUG, buffer.toString(), System.lineSeparator());
                                this.writeBuffer(buffer)
                                        .onComplete(asyncResult -> {
                                            if (asyncResult.failed()) {
                                                getLogger().exception("未能将 代理专线 收到的信息 转发给 客户端专线", asyncResult.cause());
                                            } else {
                                                getLogger().info("将 代理专线 收到的信息 转发给 客户端专线 成功");
                                            }
                                        });
                            })
                            .exceptionHandler(throwable -> {
                                getLogger().exception("代理专线 ERROR", throwable);
                            })
                            .endHandler(ended -> {
                                getLogger().info("代理专线 ENDED");
                            })
                            .closeHandler(closed -> {
                                getLogger().info("代理专线 CLOSED");
                                this.close()
                                        .eventually(v -> {
                                            return this.undeployMe();
                                        });
                            })
                    ;
                    handleReadBuffers();
                })
                .onFailure(throwable -> {
                    getLogger().exception("建立 代理专线 失败", throwable);
                });
    }

    @Override
    protected void handleBuffer(Buffer buffer) {
        // turn to socks5 proxy
        getLogger().info("将 来自 客户端专线 的报文 " + buffer.length() + " bytes 加入 待处理消息队列");
        getLogger().text(KeelLogLevel.DEBUG, buffer.toString(), System.lineSeparator());
        this.readBufferQueue.add(buffer);
        handleReadBuffers();
    }

    @Override
    protected void handleException(Throwable throwable) {
        getLogger().exception("客户端专线 ERROR", throwable);
    }

    @Override
    protected void handleEnd() {
        getLogger().info("客户端专线 END");
    }

    private synchronized void handleReadBuffers() {
        getLogger().debug("对 待处理消息队列 下手 LEN: " + this.readBufferQueue.size());
        if (this.socketToTargetServer == null) {
            getLogger().warning("代理专线还不存在，无法将待处理消息队列中的消息转发过去，等会再说");
            return;
        }
        while (!this.readBufferQueue.isEmpty()) {
            Buffer buffer = this.readBufferQueue.remove();
            this.socketToTargetServer.write(buffer)
                    .onComplete(asyncResult -> {
                        if (asyncResult.failed()) {
                            getLogger().exception("将待处理消息 转发到 代理专线 失败", asyncResult.cause());
                        } else {
                            getLogger().info("将待处理消息 转发到 代理专线 成功");
                        }
                    });
        }
    }
}
