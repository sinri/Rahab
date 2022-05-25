package io.github.sinri.Rahab.v3.wormhole;

import io.github.sinri.Rahab.v3.wormhole.transform.WormholeTransformer;
import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.KeelHelper;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

class WormholeWorkerVerticle extends KeelVerticle {
    private final NetSocket localSocket;
    private final String requestID;
    private final String wormholeName;
    private final String destinationHost;
    private final int destinationPort;
    private final NetClient remoteClient;
    private WormholeTransformer transformerForDataFromLocal;
    private WormholeTransformer transformerForDataFromRemote;

    public WormholeWorkerVerticle(NetSocket localSocket, String requestID, String wormholeName, String destinationHost, int destinationPort, NetClient remoteClient) {
        this.localSocket = localSocket;
        this.requestID = requestID;
        this.wormholeName = wormholeName;
        this.remoteClient = remoteClient;
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;
        this.transformerForDataFromLocal = null;
        this.transformerForDataFromRemote = null;
    }

    public WormholeWorkerVerticle setTransformerForDataFromLocal(WormholeTransformer transformerForDataFromLocal) {
        this.transformerForDataFromLocal = transformerForDataFromLocal;
        return this;
    }

    public WormholeWorkerVerticle setTransformerForDataFromRemote(WormholeTransformer transformerForDataFromRemote) {
        this.transformerForDataFromRemote = transformerForDataFromRemote;
        return this;
    }

    @Override
    public void start() throws Exception {
        KeelLogger wormholeLogger = Keel.standaloneLogger("WormholeWorker").setCategoryPrefix(wormholeName + "#" + requestID);
        setLogger(wormholeLogger);

        execute();
    }

    private void execute() {
        var wormholeLogger = getLogger();

        wormholeLogger.notice("近端通讯 已建立 对方地址 " + localSocket.remoteAddress().toString() + " 我方地址 " + localSocket.localAddress().toString());

        AtomicReference<NetSocket> atomicRemoteSocket = new AtomicReference<>();

        // 近端通讯 收尾配置
        localSocket
                .handler(rawBufferFromLocal -> {
                    wormholeLogger.info("近端通讯 接收到数据包 " + rawBufferFromLocal.length() + " 字节");
                    wormholeLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(rawBufferFromLocal, 40));
                    wormholeLogger.print(KeelLogLevel.DEBUG, rawBufferFromLocal.toString());

                    List<Buffer> bufferList = new ArrayList<>();
                    if (this.transformerForDataFromLocal != null) {
                        bufferList.addAll(transformerForDataFromLocal.transform(rawBufferFromLocal));
                        for (var buffer : bufferList) {
                            wormholeLogger.debug("来自近端的数据包 变形完毕 " + buffer.length() + " 字节");
                        }
                    } else {
                        bufferList.add(rawBufferFromLocal);
                    }
                    if (bufferList.size() > 0) {

                        Function<Void, Void> remoteSocketWriteBuffer = vv -> {
                            for (var buffer : bufferList) {
                                atomicRemoteSocket.get().write(buffer)
                                        .onSuccess(v -> {
                                            wormholeLogger.info("近端通讯 接收到数据包，成功转发到 远端通讯 " + buffer.length() + " 字节");
                                            wormholeLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(buffer, 40));
                                            wormholeLogger.print(KeelLogLevel.DEBUG, buffer.toString());
                                        })
                                        .onFailure(throwable -> {
                                            wormholeLogger.exception("近端通讯 接收到数据包，未能转发到 远端通讯", throwable);
                                        });
                            }
                            return null;
                        };

                        if (atomicRemoteSocket.get() == null) {
                            Keel.getVertx().setPeriodic(200L, timerID -> {
                                if (atomicRemoteSocket.get() == null) {
                                    wormholeLogger.debug("远端通讯 尚未建立，继续等待");
                                    return;
                                }
                                Keel.getVertx().cancelTimer(timerID);
                                remoteSocketWriteBuffer.apply(null);
                            });
                        } else {
                            remoteSocketWriteBuffer.apply(null);
                        }
                    }
                })
                .exceptionHandler(throwable -> {
                    wormholeLogger.exception("近端通讯 出错，近端通讯 即将关闭", throwable);
                    localSocket.close();
                })
                .closeHandler(v -> {
                    wormholeLogger.notice("近端通讯 关闭");
                    if (atomicRemoteSocket.get() != null) {
                        wormholeLogger.notice("远端通讯 即将关闭");
                        atomicRemoteSocket.get().close();
                    }

                    undeployMe();
                });

        // 建立远端通讯
        remoteClient.connect(this.destinationPort, this.destinationHost)
                .recover(throwable -> {
                    wormholeLogger.exception("远端客户端 出错，远端通讯 即将关闭", throwable);
                    localSocket.close();
                    return Future.failedFuture(throwable);
                })
                .compose(remoteSocket -> {
                    wormholeLogger.notice("远端通讯 已建立 到 " + remoteSocket.remoteAddress().toString());
                    atomicRemoteSocket.set(remoteSocket);

                    atomicRemoteSocket.get()
                            .handler(rawBufferFromRemote -> {
                                wormholeLogger.info("远端通讯 接收到数据包 " + rawBufferFromRemote.length() + " 字节");
                                wormholeLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(rawBufferFromRemote, 40));
                                wormholeLogger.print(KeelLogLevel.DEBUG, rawBufferFromRemote.toString());

                                List<Buffer> bufferList = new ArrayList<>();
                                if (this.transformerForDataFromRemote != null) {
                                    // generate mount
                                    bufferList.addAll(transformerForDataFromRemote.transform(rawBufferFromRemote));
                                    for (var buffer : bufferList) {
                                        wormholeLogger.info("来自远端的数据包 变形完毕 " + buffer.length() + " 字节");
                                    }
                                } else {
                                    bufferList.add(rawBufferFromRemote);
                                }

                                if (bufferList.size() > 0) {
                                    for (var buffer : bufferList) {
                                        localSocket.write(buffer)
                                                .onSuccess(v -> {
                                                    wormholeLogger.info("远端通讯 接收到数据包，成功转发到 近端通讯 " + buffer.length() + " 字节");
                                                    wormholeLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(buffer, 40));
                                                    wormholeLogger.print(KeelLogLevel.DEBUG, buffer.toString());
                                                })
                                                .onFailure(throwable -> {
                                                    wormholeLogger.exception("远端通讯 接收到数据包，未能转发到 近端通讯", throwable);
                                                });
                                    }
                                }
                            })
                            .exceptionHandler(throwable -> {
                                wormholeLogger.exception("远端通讯 出错，远端通讯 即将关闭", throwable);
                                atomicRemoteSocket.get().close();
                            })
                            .closeHandler(v -> {
                                wormholeLogger.notice("远端通讯 关闭");
                                //undeployMe();
                            });

                    return Future.succeededFuture();
                });

    }

}
