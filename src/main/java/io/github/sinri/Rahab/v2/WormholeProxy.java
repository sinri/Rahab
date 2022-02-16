package io.github.sinri.Rahab.v2;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 虫洞：TCP转发
 * 源 ←[近端通讯]→ 近端服务器 ↔ 远端客户端 ←[远端通讯]→ 目标
 */
public class WormholeProxy {
    /**
     * 近端服务器
     */
    protected NetServer localServer;
    private final String destinationHost;
    private final int destinationPort;

    public WormholeProxy(String destinationHost, int destinationPort) {
        this.destinationPort = destinationPort;
        this.destinationHost = destinationHost;
        localServer = Keel.getVertx().createNetServer();
    }

    public Future<NetServer> listen(int pipePort) {
        return localServer.connectHandler(localSocket -> {
                    String wormholeID = UUID.randomUUID().toString().replace("-", "");
                    KeelLogger wormholeLogger = Keel.logger("WormholeWorker").setCategoryPrefix(wormholeID);

                    wormholeLogger.notice("近端通讯 已建立 自 " + localSocket.remoteAddress().toString());

                    AtomicReference<NetClient> atomicRemoteClient = new AtomicReference<>();
                    AtomicReference<NetSocket> atomicRemoteSocket = new AtomicReference<>();

                    // 远端客户端
                    NetClient remoteClient = Keel.getVertx().createNetClient();
                    atomicRemoteClient.set(remoteClient);

                    remoteClient.connect(this.destinationPort, this.destinationHost, netSocketAsyncResult -> {
                        if (netSocketAsyncResult.failed()) {
                            wormholeLogger.exception("远端客户端 出错，近端通讯 即将关闭", netSocketAsyncResult.cause());
                            localSocket.close();
                            return;
                        }

                        // 远端通讯
                        NetSocket remoteSocket = netSocketAsyncResult.result();
                        atomicRemoteSocket.set(remoteSocket);
                        remoteSocket
                                .handler(buffer -> {
                                    wormholeLogger.debug("远端通讯 接收到数据包: " + buffer);
                                    localSocket.write(buffer)
                                            .onSuccess(v -> {
                                                wormholeLogger.info("远端通讯 接收到数据包，成功转发到 近端通讯");
                                            })
                                            .onFailure(throwable -> {
                                                wormholeLogger.exception("远端通讯 接收到数据包，未能转发到 近端通讯", throwable);
                                            });
                                })
                                .exceptionHandler(throwable -> {
                                    wormholeLogger.exception("远端通讯 出错，远端通讯 即将关闭", throwable);
                                    remoteSocket.close();
                                })
                                .closeHandler(v -> {
                                    wormholeLogger.notice("远端通讯 关闭；远端客户端 即将关闭");
                                    remoteClient.close();
                                });
                    });

                    // 近端通讯
                    localSocket
                            .handler(buffer -> {
                                wormholeLogger.debug("近端通讯 接收到数据包: " + buffer);
                                if(atomicRemoteSocket.get()==null){
                                   Keel.getVertx().setPeriodic(100L,timerID->{
                                       if(atomicRemoteSocket.get()!=null){
                                           atomicRemoteSocket.get().write(buffer)
                                                   .onSuccess(v -> {
                                                       wormholeLogger.info("近端通讯 接收到数据包，成功转发到 远端通讯");
                                                   })
                                                   .onFailure(throwable -> {
                                                       wormholeLogger.exception("近端通讯 接收到数据包，未能转发到 远端通讯", throwable);
                                                   });
                                           Keel.getVertx().cancelTimer(timerID);
                                       }
                                       // else: wait for next
                                   });
                                }else {
                                    atomicRemoteSocket.get().write(buffer)
                                            .onSuccess(v -> {
                                                wormholeLogger.info("近端通讯 接收到数据包，成功转发到 远端通讯");
                                            })
                                            .onFailure(throwable -> {
                                                wormholeLogger.exception("近端通讯 接收到数据包，未能转发到 远端通讯", throwable);
                                            });
                                }
                            })
                            .exceptionHandler(throwable -> {
                                wormholeLogger.exception("近端通讯 出错，近端通讯 即将关闭", throwable);
                                localSocket.close();
                            })
                            .closeHandler(v -> {
                                wormholeLogger.notice("近端通讯 关闭");
                                if (atomicRemoteClient.get() != null) {
                                    wormholeLogger.notice("远端通讯 即将关闭");
                                    atomicRemoteClient.get().close();
                                }
                            });
                })
                .exceptionHandler(throwable -> {
                    Keel.logger("WormholeProxy").exception("近端服务器 出错", throwable);
                })
                .listen(pipePort);
    }
}
