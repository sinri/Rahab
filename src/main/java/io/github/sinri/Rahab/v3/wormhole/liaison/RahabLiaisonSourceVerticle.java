package io.github.sinri.Rahab.v3.wormhole.liaison;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @since 3.0.0
 */
public class RahabLiaisonSourceVerticle extends KeelVerticle {
    /**
     * 情报源客户端
     */
    protected NetClient clientToBroker;
    protected String liaisonSourceName;
    protected NamedDataProcessor namedDataProcessor;
    protected SourceWorkerGenerator sourceWorkerGenerator;
    private final String brokerHost;
    private final int brokerPort;

    private boolean runAsUniqueDaemon = true;

    public RahabLiaisonSourceVerticle(String liaisonSourceName, String brokerHost, int brokerPort) {
        this.clientToBroker = Keel.getVertx().createNetClient();
        this.liaisonSourceName = liaisonSourceName;
        this.namedDataProcessor = new NamedDataProcessor();
        this.sourceWorkerGenerator = null;
        this.brokerHost = brokerHost;
        this.brokerPort = brokerPort;
    }

    public RahabLiaisonSourceVerticle setRunAsUniqueDaemon(boolean runAsUniqueDaemon) {
        this.runAsUniqueDaemon = runAsUniqueDaemon;
        return this;
    }

    public RahabLiaisonSourceVerticle setSourceWorkerGenerator(SourceWorkerGenerator sourceWorkerGenerator) {
        this.sourceWorkerGenerator = sourceWorkerGenerator;
        return this;
    }

    @Override
    public void start() throws Exception {
        var logger = Keel.outputLogger("RahabLiaisonSourceVerticle");
        setLogger(logger);

        establishConnectionToBroker();
    }

    private void establishConnectionToBroker() {
        var logger = getLogger();
        AtomicInteger pingFailedCounter = new AtomicInteger(0);

        this.clientToBroker.connect(brokerPort, brokerHost)
                .recover(throwable -> {
                    getLogger().exception("情报源客户端连接掮客失败 准备退出", throwable);

                    if (runAsUniqueDaemon) {
                        return Keel.getVertx().close().compose(v -> Future.failedFuture("情报源客户端连接掮客失败退出"));
                    } else {
                        return undeployMe().compose(v -> Future.failedFuture("情报源客户端连接掮客失败退出"));
                    }
                })
                .compose(socketWithBroker -> {
                    socketWithBroker
                            .handler(buffer -> {
                                List<NamedDataProcessor.NamedData> list = this.namedDataProcessor.parseAll(buffer);
                                for (var item : list) {
                                    String clientID = item.getClientID();
                                    Buffer rawBufferFromClient = item.getRawBuffer();

                                    if (clientID.equals("BrokerPong")) {
                                        logger.debug("收到掮客发来的生存确认 " + rawBufferFromClient.toString());
                                        continue;
                                    }

                                    if (clientID.equals("BrokerNotifyClientGone")) {
                                        String deadClientID = item.getRawBuffer().toString();
                                        logger.warning("收到掮客发来的客户端讣告 " + deadClientID);
                                        sourceWorkerGenerator.removeWorkerForClient(deadClientID);
                                        continue;
                                    }

                                    logger.info("RahabLiaisonSource 收到掮客发来的来自客户端 " + clientID + " 的数据 " + rawBufferFromClient.length() + " 字节");

                                    // check map if existed worker for this client
                                    sourceWorkerGenerator.getWorkerForClient(clientID, socketWithBroker, logger)
                                            .onFailure(throwable -> {
                                                logger.exception("RahabLiaisonSource 无法生成 RahabLiaisonSourceWorker，毁灭吧", throwable);
                                                socketWithBroker.close();
                                            })
                                            .compose(rahabLiaisonSourceWorker -> {
                                                logger.info("已获取 rahabLiaisonSourceWorker");
                                                // handle buffer
                                                rahabLiaisonSourceWorker.handle(rawBufferFromClient);
                                                return Future.succeededFuture();
                                            });
                                }
                            })
                            .exceptionHandler(throwable -> {
                                logger.exception("RahabLiaisonSource 与掮客的通讯 出错", throwable);
                            })
                            .closeHandler(v -> {
                                logger.notice("RahabLiaisonSource 与掮客的通讯关闭；掮客客户端即将关闭。");

                                Keel.getVertx().setTimer(1000L, timerID -> {
                                    logger.notice("RahabLiaisonSource 准备重启与掮客的通讯");

                                    this.establishConnectionToBroker();
                                });
                            })
                    ;

                    // PING per minutes
                    Keel.getVertx().setPeriodic(30000L, timerID -> {
                        Buffer pingBufferToBroker = NamedDataProcessor.makeNamedDataBuffer(
                                Buffer.buffer("BROKER, SOURCE " + liaisonSourceName + " IS STILL ALIVE"),
                                "SourcePing"
                        );
                        socketWithBroker.write(pingBufferToBroker)
                                .onComplete(voidAsyncResult -> {
                                    if (voidAsyncResult.failed()) {
                                        int currentFailedCount = pingFailedCounter.incrementAndGet();
                                        logger.exception("Source Ping Sending Failed * " + currentFailedCount, voidAsyncResult.cause());
                                    } else {
                                        logger.debug("Source Ping Sent");
                                        pingFailedCounter.set(0);
                                    }
                                });
                    });

                    return socketWithBroker.write("Nyanpasu! RahabProxyBroker, I am the proxy " + liaisonSourceName + "!")
                            .onFailure(throwable -> {
                                logger.exception("RahabLiaisonSource [" + liaisonSourceName + "] 未能发送登记数据给掮客，即将关闭", throwable);
                                socketWithBroker.close();

                                if (runAsUniqueDaemon) {
                                    Keel.getVertx().close();
                                } else {
                                    undeployMe();
                                }
                            })
                            .onSuccess(v -> {
                                logger.notice("RahabLiaisonSource [" + liaisonSourceName + "] 发送登记数据给掮客成功");
                            });
                });
    }
}
