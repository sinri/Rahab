package io.github.sinri.rahab.v4.wormhole.liaison;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RahabLiaisonBrokerWorkerVerticle extends KeelVerticle {
    static Pattern patternForProxyRegister = Pattern.compile("^Nyanpasu! RahabProxyBroker, I am the proxy ([A-Za-z0-9.]+)!$");

    private final NetSocket socket;
    private final String requestID;
    private final ClientSocketManager clientSocketManager;

    /**
     * 数据包处理器
     */
    private final NamedDataProcessor namedDataProcessor;

    public RahabLiaisonBrokerWorkerVerticle(NetSocket socket, String requestID, ClientSocketManager clientSocketManager) {
        this.socket = socket;
        this.requestID = requestID;
        this.namedDataProcessor = new NamedDataProcessor();
        this.clientSocketManager = clientSocketManager;
    }

    @Override
    public void start() throws Exception {
        KeelLogger sourceRegistrationLogger = Keel.standaloneLogger("ProxyBrokerSourceRegistration");

        KeelLogger socketLogger = Keel.standaloneLogger("ProxyBrokerSocket").setCategoryPrefix(requestID);
        setLogger(socketLogger);

        AtomicReference<SOCKET_ROLE> atomicSocketRole = new AtomicReference<>(SOCKET_ROLE.INIT);
        AtomicInteger pongFailedCounter = new AtomicInteger(0);

        socket
                .handler(buffer -> {
                    if (atomicSocketRole.get().equals(SOCKET_ROLE.INIT)) {
                        socketLogger.notice("INIT BUFFER [" + buffer + "]");
                        Matcher matcherForProxyRegister = patternForProxyRegister.matcher(buffer.toString());
                        if (matcherForProxyRegister.find()) {
                            String proxyName = matcherForProxyRegister.group(1);
                            if (this.clientSocketManager.getProxySocket() != null) {
                                socketLogger.warning("代理卧底 已经存在 先关闭之");
                                sourceRegistrationLogger.warning("即将关闭当前的 代理卧底 通信, 登记名称为 " + proxyName);
                                this.clientSocketManager.getProxySocket().close();
                            }
                            socketLogger.notice("登记新的代理卧底 " + proxyName + " 于 " + socket.remoteAddress().toString());
                            sourceRegistrationLogger.notice("登记新的代理卧底, 登记名称为 " + proxyName + " 潜伏地址为 " + socket.remoteAddress().toString() + " socket id 为 " + requestID);
                            this.clientSocketManager.registerProxySocket(socket);
                            atomicSocketRole.set(SOCKET_ROLE.PROXY);
                            // then wait for next buffer
                            return;
                        } else {
                            // mark it as client
                            atomicSocketRole.set(SOCKET_ROLE.CLIENT);
                            // find a proxy for it
                            if (this.clientSocketManager.getProxySocket() == null) {
                                socket.write("50万还没有到账");
                                socketLogger.error("代理卧底通讯映射为空，找不到可用代理卧底，即将关闭 与客户端 [" + requestID + "] 的 通讯");
                                socket.close();
                                return;
                            }
                            // register for later processing it as client
                            this.clientSocketManager.getClientSocketMap().put(requestID, socket);
                        }
                    }

                    if (atomicSocketRole.get().equals(SOCKET_ROLE.CLIENT)) {
                        if (this.clientSocketManager.getProxySocket() == null) {
                            socketLogger.error("代理卧底 不存在；与客户端的通信 即将关闭");
                            socket.close();
                            return;
                        }
                        // 准备发给代理卧底的数据包
                        Buffer bufferToSendToProxy = NamedDataProcessor.makeNamedDataBuffer(buffer, requestID);
                        // send to proxy
                        clientSocketManager.getProxySocket().write(bufferToSendToProxy)
                                .onComplete(voidAsyncResult -> {
                                    if (voidAsyncResult.failed()) {
                                        socketLogger.exception("发送数据给 代理卧底 失败；与客户端的通信即将关闭", voidAsyncResult.cause());
                                        socket.close();
                                    } else {
                                        socketLogger.info("发送数据给 代理卧底 成功，共计 " + bufferToSendToProxy.length() + " 字节，其中原始数据 " + buffer.length() + " 字节");
                                    }
                                });
                    } else if (atomicSocketRole.get().equals(SOCKET_ROLE.PROXY)) {
                        // 天啦撸 代理卧底回信了 解析后转发给各自的client
                        List<NamedDataProcessor.NamedData> list = this.namedDataProcessor.parseAll(buffer);
                        for (var item : list) {
                            String clientID = item.getClientID();
                            Buffer rawBufferFromProxyToClient = item.getRawBuffer();

                            if (clientID.equals("SourcePing")) {
                                socketLogger.debug("收到 代理卧底 发来的 生存确认");
                                sourceRegistrationLogger.debug("Source Ping: " + rawBufferFromProxyToClient.toString());

                                Buffer pongBufferToSource = NamedDataProcessor.makeNamedDataBuffer(
                                        Buffer.buffer("ROGER, BROKER IS WELL, TOO."),
                                        "BrokerPong"
                                );
                                socket.write(pongBufferToSource)
                                        .onComplete(voidAsyncResult -> {
                                            if (voidAsyncResult.failed()) {
                                                int currentFailedCount = pongFailedCounter.incrementAndGet();
                                                socketLogger.exception("Broker Pong Sending Failed * " + currentFailedCount, voidAsyncResult.cause());

                                                if (currentFailedCount >= 5) {
                                                    socketLogger.fatal("Broker Pong 连续5次暴毙！撤销PING的定时器并准备重启。");
                                                    sourceRegistrationLogger.fatal("代理卧底 发来的PING已经5次没法PONG回去了");
                                                }
                                            } else {
                                                socketLogger.debug("Broker Pong Sent");
                                                pongFailedCounter.set(0);
                                            }
                                        });
                                continue;
                            }

                            NetSocket socketToTargetClient = this.clientSocketManager.getClientSocketMap().get(clientID);
                            if (socketToTargetClient == null) {
                                socketLogger.warning("代理卧底 发来给 客户端 [" + clientID + "] 的数据 " + rawBufferFromProxyToClient.length() + " 字节 因 客户端不存在，丢弃");
                                continue;
                            }

                            socketToTargetClient.write(rawBufferFromProxyToClient)
                                    .onComplete(voidAsyncResult -> {
                                        if (voidAsyncResult.failed()) {
                                            socketLogger.exception("发送数据给 客户端 [" + clientID + "] 失败，准备清除客户端缓存", voidAsyncResult.cause());
                                            this.clientSocketManager.getClientSocketMap().remove(clientID).close();
                                        } else {
                                            socketLogger.info("发送数据给 客户端 [" + clientID + "] 成功，共计 " + rawBufferFromProxyToClient.length() + " 字节");
                                        }
                                    });
                        }
                    }
                    // else: no such possibility
                })
                .exceptionHandler(throwable -> {
                    socketLogger.exception("通讯出错", throwable);
                    if (atomicSocketRole.get() == SOCKET_ROLE.INIT) {
                        socketLogger.error("即将关闭 这个 INIT 通讯");
                        socket.close();
                    } else if (atomicSocketRole.get() == SOCKET_ROLE.CLIENT) {
                        socketLogger.error("即将关闭 这个 CLIENT 通讯");
                        this.clientSocketManager.getClientSocketMap().remove(requestID);
                        socket.close();
                    } else if (atomicSocketRole.get() == SOCKET_ROLE.PROXY) {
                        socketLogger.error("即将关闭 这个 PROXY 通讯");
                        this.clientSocketManager.unregisterProxySocket();
                        socket.close();
                    }
                })
                .closeHandler(v -> {
                    socketLogger.notice("关闭 " + atomicSocketRole.get() + " 通信");

                    if (atomicSocketRole.get() == SOCKET_ROLE.PROXY) {
                        sourceRegistrationLogger.warning("当前 代理通信 关闭");
                        this.clientSocketManager.unregisterProxySocket();
                    } else if (atomicSocketRole.get() == SOCKET_ROLE.CLIENT) {
                        // let source know the client is gone
                        NetSocket proxySocket = this.clientSocketManager.getProxySocket();
                        if (proxySocket != null) {
                            Buffer notifyClientGoneBuffer = NamedDataProcessor.makeNamedDataBuffer(
                                    Buffer.buffer(requestID),
                                    "BrokerNotifyClientGone"
                            );
                            proxySocket.write(notifyClientGoneBuffer)
                                    .compose(written -> {
                                        getLogger().info("BrokerNotifyClientGone Sent");
                                        return Future.succeededFuture();
                                    })
                                    .recover(throwable -> {
                                        getLogger().exception("Failed to send BrokerNotifyClientGone", throwable);
                                        return Future.succeededFuture();
                                    });
                        }
                    }
                });
    }

    protected enum SOCKET_ROLE {
        INIT, CLIENT, PROXY
    }
}
