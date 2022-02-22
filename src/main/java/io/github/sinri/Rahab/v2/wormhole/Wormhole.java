package io.github.sinri.Rahab.v2.wormhole;

import io.github.sinri.Rahab.v2.wormhole.transform.WormholeTransformer;
import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.KeelHelper;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 虫洞：TCP转发
 * 源 ←[近端通讯]→ 近端服务器 ↔ 远端客户端 ←[远端通讯]→ 目标
 */
public class Wormhole {
    /**
     * 近端服务器
     */
    protected NetServer localServer;
    /**
     * 远端客户端
     */
    protected NetClient remoteClient;

    private final String wormholeName;
    private final String destinationHost;
    private final int destinationPort;

    private WormholeTransformer transformerForDataFromLocal;
    private WormholeTransformer transformerForDataFromRemote;

    public Wormhole(String wormholeName, String destinationHost, int destinationPort) {
        this.wormholeName = wormholeName;
        this.destinationPort = destinationPort;
        this.destinationHost = destinationHost;
        localServer = Keel.getVertx().createNetServer();
        this.transformerForDataFromLocal = null;
        this.transformerForDataFromRemote = null;
        this.remoteClient = Keel.getVertx().createNetClient();
    }

    /**
     * 加密中继模式中用于处理来自近端通讯的数据
     *
     * @param transformerForDataFromLocal WormholeTransformer
     * @return this
     */
    public Wormhole setTransformerForDataFromLocal(WormholeTransformer transformerForDataFromLocal) {
        this.transformerForDataFromLocal = transformerForDataFromLocal;
        return this;
    }

    /**
     * 加密中继模式中用于处理来自远端通讯的数据
     *
     * @param transformerForDataFromRemote WormholeTransformer
     * @return this
     */
    public Wormhole setTransformerForDataFromRemote(WormholeTransformer transformerForDataFromRemote) {
        this.transformerForDataFromRemote = transformerForDataFromRemote;
        return this;
    }


    public Future<NetServer> listen(int pipePort) {
        return localServer.connectHandler(localSocket -> {
                    String wormholeID = UUID.randomUUID().toString().replace("-", "");
                    KeelLogger wormholeLogger = Keel.standaloneLogger("WormholeWorker").setCategoryPrefix(wormholeName + "#" + wormholeID);

                    wormholeLogger.notice("近端通讯 已建立 对方地址 " + localSocket.remoteAddress().toString() + " 我方地址 " + localSocket.localAddress().toString());

//                    AtomicReference<NetClient> atomicRemoteClient = new AtomicReference<>();
                    AtomicReference<NetSocket> atomicRemoteSocket = new AtomicReference<>();

                    // 远端客户端
//                    NetClient remoteClient = Keel.getVertx().createNetClient();
//                    atomicRemoteClient.set(remoteClient);

                    remoteClient.connect(this.destinationPort, this.destinationHost, netSocketAsyncResult -> {
                        if (netSocketAsyncResult.failed()) {
                            wormholeLogger.exception("远端客户端 出错，近端通讯 即将关闭", netSocketAsyncResult.cause());
                            localSocket.close();
                            return;
                        }

                        // 远端通讯
                        NetSocket remoteSocket = netSocketAsyncResult.result();
                        wormholeLogger.notice("远端通讯 已建立 到 " + remoteSocket.remoteAddress().toString());
                        atomicRemoteSocket.set(remoteSocket);
                        remoteSocket
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
//                                        new FutureWhile<Integer>(
//                                                0,
//                                                integerFutureWhile -> integerFutureWhile.getLastValue()>= bufferList.size(),
//                                                integer -> {
//                                                    Buffer buffer = bufferList.get(integer);
//                                                    return localSocket.write(buffer)
//                                                            .onSuccess(v -> {
//                                                                wormholeLogger.info("远端通讯 接收到数据包，成功转发到 近端通讯 " + buffer.length() + " 字节");
//                                                                wormholeLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(buffer, 40));
//                                                                wormholeLogger.print(KeelLogLevel.DEBUG, buffer.toString());
//                                                            })
//                                                            .onFailure(throwable -> {
//                                                                wormholeLogger.exception("远端通讯 接收到数据包，未能转发到 近端通讯", throwable);
//                                                            })
//                                                            .compose(v->{
//                                                                return Future.succeededFuture(integer+1);
//                                                            });
//                                                }
//                                        )
//                                                .runInWhile()
//                                                .eventually(v->{
//                                                    wormholeLogger.info("All buffers written");
//                                                    return Future.succeededFuture();
//                                                });
//                                        ;

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
                                    remoteSocket.close();
                                })
                                .closeHandler(v -> {
                                    wormholeLogger.notice("远端通讯 关闭");
//                                    wormholeLogger.notice("远端通讯 关闭；远端客户端 即将关闭");
//                                    remoteClient.close();
                                });
                    });

                    // 近端通讯
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
//                                        new FutureWhile<Integer>(
//                                                0,
//                                                integerFutureWhile -> integerFutureWhile.getLastValue()>= bufferList.size(),
//                                                integer -> {
//                                                    var buffer=bufferList.get(integer);
//                                                    return atomicRemoteSocket.get().write(buffer)
//                                                            .onSuccess(v -> {
//                                                                wormholeLogger.info("近端通讯 接收到数据包，成功转发到 远端通讯 " + buffer.length() + " 字节");
//                                                                wormholeLogger.print(KeelLogLevel.DEBUG, KeelHelper.bufferToHexMatrix(buffer, 40));
//                                                                wormholeLogger.print(KeelLogLevel.DEBUG, buffer.toString());
//                                                            })
//                                                            .onFailure(throwable -> {
//                                                                wormholeLogger.exception("近端通讯 接收到数据包，未能转发到 远端通讯", throwable);
//                                                            })
//                                                            .compose(v->{
//                                                                return Future.succeededFuture(integer+1);
//                                                            });
//                                                }
//                                        ).runInWhile();

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
//                                if (atomicRemoteClient.get() != null) {
//                                    wormholeLogger.notice("远端通讯 即将关闭");
//                                    atomicRemoteClient.get().close();
//                                }
                            });
                })
                .exceptionHandler(throwable -> {
                    Keel.logger("WormholeProxy").exception("近端服务器 出错", throwable);
                })
                .listen(pipePort);
    }

}
