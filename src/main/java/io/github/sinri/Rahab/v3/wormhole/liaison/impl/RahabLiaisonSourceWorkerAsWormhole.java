package io.github.sinri.Rahab.v3.wormhole.liaison.impl;

import io.github.sinri.Rahab.v3.wormhole.liaison.NamedDataProcessor;
import io.github.sinri.Rahab.v3.wormhole.liaison.RahabLiaisonSourceWorker;
import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.LinkedList;
import java.util.Queue;

public class RahabLiaisonSourceWorkerAsWormhole implements RahabLiaisonSourceWorker {
    protected String clientID;
    protected KeelLogger logger = KeelLogger.silentLogger();

    protected String destinationHost;
    protected int destinationPort;
    protected NetClient wormholeClient;
    protected NetSocket wormholeSocket;
    protected NetSocket brokerSocket;

    protected Queue<Buffer> bufferQueue;

    public RahabLiaisonSourceWorkerAsWormhole(String destinationHost, int destinationPort) {
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;
        this.wormholeClient = Keel.getVertx().createNetClient();
        this.wormholeSocket = null;
        this.bufferQueue = new LinkedList<>();
    }

    @Override
    public Future<Void> initialize() {
        return this.wormholeClient.connect(destinationPort, destinationHost)
                .onFailure(throwable -> {
                    logger.exception("RahabLiaisonSourceWorkerAsWormhole initialize 失败", throwable);
                })
                .compose(socket -> {
                    logger.info("与 虫洞 " + socket.remoteAddress().toString() + " 的通讯 成功建立");
                    socket.handler(bufferFromWormhole -> {
                                Buffer bufferToBroker = NamedDataProcessor.makeNamedDataBuffer(bufferFromWormhole, clientID);

                                this.brokerSocket.write(bufferToBroker)
                                        .onComplete(voidAsyncResult -> {
                                            if (voidAsyncResult.failed()) {
                                                logger.exception("RahabLiaisonSourceWorkerAsWormhole 自 虫洞 往 情报源 搬运打包后的数据 失败", voidAsyncResult.cause());
                                                this.close();
                                            } else {
                                                logger.info("RahabLiaisonSourceWorkerAsWormhole 自 虫洞 往 情报源 搬运打包后的数据 成功 " + bufferToBroker.length() + "字节 其中来自虫洞的数据 " + bufferFromWormhole.length() + " 字节");
                                                logger.debug(bufferToBroker.toString());
                                            }
                                        });
                            })
                            .exceptionHandler(throwable -> {
                                logger.exception("RahabLiaisonSourceWorkerAsWormhole 与虫洞通讯出错", throwable);
                            })
                            .closeHandler(v -> {
                                logger.notice("RahabLiaisonSourceWorkerAsWormhole 与虫洞的通讯关闭");
                            });

                    this.wormholeSocket = socket;
                    logger.info("与 虫洞 " + socket.remoteAddress().toString() + " 的通讯 配置完成");
                    return Future.succeededFuture();
                });
    }

    @Override
    public RahabLiaisonSourceWorker setClientID(String clientID) {
        this.clientID = clientID;
        return this;
    }

    @Override
    public RahabLiaisonSourceWorker setClientSocket(NetSocket clientSocket) {
        this.brokerSocket = clientSocket;
        return this;
    }

    @Override
    public RahabLiaisonSourceWorker setLogger(KeelLogger logger) {
        this.logger = logger;
        return this;
    }

    public void pump() {
        logger.debug("start pump");
        while (true) {
            Buffer buffer = this.bufferQueue.poll();
            if (buffer == null) break;

            logger.debug("pumping buffer " + buffer.length() + " bytes");

            this.wormholeSocket.write(buffer)
                    .onComplete(voidAsyncResult -> {
                        if (voidAsyncResult.failed()) {
                            logger.exception("RahabLiaisonSourceWorkerAsWormhole 自 情报源 往 虫洞 搬运数据失败", voidAsyncResult.cause());
                            this.close();
                        } else {
                            logger.info("RahabLiaisonSourceWorkerAsWormhole 自 情报源 往 虫洞 搬运数据 " + buffer.length() + "字节");
                            logger.debug(buffer.toString());
                        }
                    });
        }
    }

    @Override
    public void handle(Buffer rawBufferFromClient) {
        this.bufferQueue.offer(rawBufferFromClient);

        if (this.wormholeSocket == null) {
            logger.debug("wormholeSocket still null, start wait");
            Keel.getVertx().setPeriodic(100L, timerID -> {
                if (this.wormholeSocket == null) {
                    logger.debug("wormholeSocket still null, wait");
                    return;
                }
                Keel.getVertx().cancelTimer(timerID);

                // handle!
                pump();
            });
        } else {
            pump();
        }
    }

    @Override
    public Future<Void> close() {
        logger.notice("RahabLiaisonSourceWorkerAsWormhole close");
        return this.wormholeSocket.close().eventually(v -> this.wormholeClient.close());
    }
}
