package io.github.sinri.Rahab.v2.wormhole.liaison;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RahabLiaisonSource {
    /**
     * 情报源客户端
     */
    protected NetClient clientToBroker;
    protected String liaisonSourceName;
    protected NamedDataProcessor namedDataProcessor;
    protected SourceWorkerGenerator sourceWorkerGenerator;

    public RahabLiaisonSource(String liaisonSourceName) {
        this.clientToBroker = Keel.getVertx().createNetClient();
        this.liaisonSourceName = liaisonSourceName;
        this.namedDataProcessor = new NamedDataProcessor();
        this.sourceWorkerGenerator = null;
    }

    public RahabLiaisonSource setSourceWorkerGenerator(SourceWorkerGenerator sourceWorkerGenerator) {
        this.sourceWorkerGenerator = sourceWorkerGenerator;
        return this;
    }

    public Future<Void> start(String brokerHost, int brokerPort) {
        KeelLogger logger = Keel.logger("RahabLiaisonSource");

        AtomicInteger pingFailedCounter = new AtomicInteger(0);

        return this.clientToBroker
                .connect(brokerPort, brokerHost)
                .onFailure(throwable -> {
                    logger.exception("RahabLiaisonSource 情报源客户端连接掮客失败", throwable);
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
                                    this.start(brokerHost, brokerPort);
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

//                                        if (currentFailedCount >= 5) {
//                                            Keel.getVertx().cancelTimer(timerID);
//                                            logger.fatal("Source Ping 连续5次暴毙！撤销PING的定时器并关闭通信。");
//
//                                            //this.start(brokerHost, brokerPort);
//                                            socketWithBroker.close();
//                                        }
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
                            })
                            .onSuccess(v -> {
                                logger.notice("RahabLiaisonSource [" + liaisonSourceName + "] 发送登记数据给掮客成功");
                            });
                });
    }
}
