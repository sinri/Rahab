package io.github.sinri.rahab.v4.liaison.source;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.controlflow.FutureForEach;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4.liaison.entity.LiaisonEnvelope;
import io.github.sinri.rahab.v4.liaison.entity.LiaisonEnvelopeProcessor;
import io.github.sinri.rahab.v4.liaison.entity.SocketWrapper;
import io.github.sinri.rahab.v4.liaison.meta.SourceOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RahabLiaisonSource {
    private final String sourceName;
    private final String brokerHost;
    private final int brokerPort;
    private final NetClient netClient;
    private NetSocket socketToBroker;
    private KeelLogger logger;
    private final LiaisonEnvelopeProcessor brokerLiaisonEnvelopeProcessor = new LiaisonEnvelopeProcessor();
    private final Map<String, SocketWrapper> socketMapToTerminals = new HashMap<>();
    private final RahabLiaisonSourceWorker worker;

    public RahabLiaisonSource(SourceOptions sourceOptions) {
        this.sourceName = sourceOptions.getSourceName();
        this.brokerPort = sourceOptions.getBrokerPort();
        this.brokerHost = sourceOptions.getBrokerHost();

        this.netClient = Keel.getVertx().createNetClient();
        this.logger = Keel.outputLogger("源");

        this.worker = new RahabLiaisonSourceWorker(this.netClient, sourceOptions.getProxyHost(), sourceOptions.getProxyPort());
    }

    public RahabLiaisonSource setLogger(KeelLogger logger) {
        this.logger = logger;
        return this;
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void run() {
        getLogger().setContentPrefix("");
        connectToBroker();
    }

    private void connectToBroker() {
        this.netClient.connect(brokerPort, brokerHost)
                .onComplete(netSocketAsyncResult -> {
                    if (netSocketAsyncResult.failed()) {
                        getLogger().exception("连接到掮客失败", netSocketAsyncResult.cause());
                        Keel.getVertx().close();
                        return;
                    }

                    socketToBroker = netSocketAsyncResult.result();
                    getLogger().setContentPrefix("<与掮客的通信>");

                    socketToBroker
                            .handler(bufferFromBroker -> {
                                getLogger().debug("掮客发来了数据 " + bufferFromBroker + " 字节");
                                List<LiaisonEnvelope> liaisonEnvelopes = brokerLiaisonEnvelopeProcessor.parseAll(bufferFromBroker);
                                FutureForEach.call(liaisonEnvelopes, liaisonEnvelope -> {
                                    String terminalName = liaisonEnvelope.getContentHolder();
                                    Buffer bufferFromTerminal = liaisonEnvelope.getContentBuffer();

                                    getLogger().debug("掮客发来的信封 来自 终端 [" + terminalName + "]");
                                    getLogger().buffer(bufferFromTerminal);
                                    if (Objects.equals(terminalName, this.sourceName) && "REGISTERED".equals(bufferFromTerminal.toString())) {
                                        getLogger().notice("源在掮客注册成功");
                                        return Future.succeededFuture();
                                    }

                                    return this.worker.handleTerminalRequest(terminalName, bufferFromTerminal)
                                            .compose(v -> {
                                                getLogger().info("准备转发给终端 [" + terminalName + "] 的消息 发给掮客成功");
                                                return Future.succeededFuture();
                                            }, throwable -> {
                                                getLogger().exception(KeelLogLevel.WARNING, "准备转发给终端 [" + terminalName + "] 的消息 发给掮客失败", throwable);
                                                return Future.succeededFuture();
                                            });
                                });
                            })
                            .endHandler(ended -> {
                                getLogger().info("socketToBroker 读到结尾");
                            })
                            .drainHandler(drain -> {
                                getLogger().info("socketToBroker DRAIN");
                                socketToBroker.resume();
                            })
                            .exceptionHandler(throwable -> {
                                getLogger().exception("连接读取异常", throwable);
                            })
                            .closeHandler(closed -> {
                                getLogger().info("通信关闭，结束服务");
                                Keel.getVertx().close();
                            });

                    worker.setSocketToBroker(this.socketToBroker);

                    // register
                    socketToBroker.write(LiaisonEnvelope.buildForSourceToRegister(this.sourceName).toBuffer())
                            .onComplete(asyncResult -> {
                                if (asyncResult.failed()) {
                                    getLogger().exception("源向掮客注册 失败 关闭与掮客的通信", asyncResult.cause());
                                    socketToBroker.close();
                                } else {
                                    getLogger().info("源向掮客注册 成功");
                                }
                            });
                    if (socketToBroker.writeQueueFull()) {
                        socketToBroker.pause();
                    }
                });
    }
}

