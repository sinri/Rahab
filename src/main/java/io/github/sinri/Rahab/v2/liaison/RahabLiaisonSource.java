package io.github.sinri.Rahab.v2.liaison;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;

import java.util.List;

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
                                logger.notice("RahabLiaisonSource 与掮客的通讯 关闭；掮客客户端 即将关闭");
                                this.clientToBroker.close();
                            })
                    ;
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
