package io.github.sinri.rahab.v4.liaison.source;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4.liaison.entity.LiaisonEnvelope;
import io.github.sinri.rahab.v4.liaison.entity.SocketWrapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.HashMap;
import java.util.Map;

public class RahabLiaisonSourceWorker {
    private NetSocket socketToBroker = null;
    private final Map<String, SocketWrapper> socketMapToTerminals = new HashMap<>();
    private final NetClient netClient;
    private final KeelLogger logger;
    private final String proxyHost;
    private final int proxyPort;

    public RahabLiaisonSourceWorker(
            NetClient netClient,
            String proxyHost,
            int proxyPort
    ) {
        this.netClient = netClient;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.logger = Keel.outputLogger("源处理");
    }

    public RahabLiaisonSourceWorker setSocketToBroker(NetSocket socketToBroker) {
        this.socketToBroker = socketToBroker;
        return this;
    }

    public KeelLogger getLogger() {
        return logger;
    }

    private Future<SocketWrapper> getSocketWrapperForTerminal(String terminalName) {
        if (socketMapToTerminals.containsKey(terminalName)) {
            SocketWrapper socketWrapper = socketMapToTerminals.get(terminalName);
            if (socketWrapper != null) {
                return Future.succeededFuture(socketWrapper);
            }
            socketMapToTerminals.remove(terminalName);
        }
        return createNetSocketForTerminalRequest(terminalName)
                .compose(socketForTerminal -> {
                    getLogger().info("建立目标通信");
                    socketForTerminal
                            .handler(bufferToTerminal -> {
                                getLogger().debug("收到来自目标通信的数据 " + bufferToTerminal.length() + " 字节");
                                getLogger().buffer(bufferToTerminal);
                                if (socketToBroker == null) {
                                    getLogger().error("与掮客的通信不可用，关闭目标通信");
                                    socketForTerminal.close();
                                } else {
                                    socketToBroker.write(LiaisonEnvelope.buildForSourceToTransfer(terminalName, bufferToTerminal).toBuffer())
                                            .onComplete(asyncResult -> {
                                                if (asyncResult.failed()) {
                                                    getLogger().exception("与掮客的通信不可达，关闭目标通信", asyncResult.cause());
                                                    socketForTerminal.close();
                                                } else {
                                                    getLogger().info("与掮客的通信成功");
                                                }
                                            });
                                }
                            })
                            .endHandler(ended -> {
                                getLogger().info("目标通信 读取完毕");
                            })
                            .drainHandler(drain -> {
                                getLogger().info("目标通信 DRAIN");
                                socketForTerminal.resume();
                            })
                            .exceptionHandler(throwable -> {
                                getLogger().exception("目标通信 异常", throwable);
                            })
                            .closeHandler(closed -> {
                                getLogger().notice("目标通信 关闭，通知掮客关闭终端 [" + terminalName + "]");
                                socketMapToTerminals.remove(terminalName);
                                // send a TRANSFER with EMPTY buffer, let broker close socket with terminate
                                socketToBroker.write(LiaisonEnvelope.buildForSourceToTransfer(terminalName, Buffer.buffer()).toBuffer());
                            });


                    SocketWrapper socketWrapper = new SocketWrapper();
                    socketWrapper.update(terminalName, socketForTerminal);
                    return Future.succeededFuture(socketWrapper);
                });
    }

    protected Future<NetSocket> createNetSocketForTerminalRequest(String terminalName) {
        getLogger().debug("为终端 [" + terminalName + "] 建立目标通信");
        return netClient.connect(proxyPort, proxyHost);
    }

    public Future<Void> handleTerminalRequest(String terminalName, Buffer bufferFromTerminal) {
        this.logger.setContentPrefix("<" + terminalName + ">");
        getLogger().debug("处理来自终端的请求", new JsonObject()
                .put("terminal_name", terminalName)
                .put("buffer", bufferFromTerminal.toString())
        );
        return getSocketWrapperForTerminal(terminalName)
                .onFailure(throwable -> {
                    getLogger().exception("目标通信无法获取 对应终端 " + terminalName, throwable);
                })
                .compose(socketWrapper -> {
                    return socketWrapper.write(bufferFromTerminal)
                            .compose(written -> {
                                return Future.succeededFuture();
                            }, throwable -> {
                                getLogger().error("目标通信 丢失 对应终端 " + terminalName);
                                socketMapToTerminals.remove(terminalName);
                                return Future.failedFuture("socketForTerminal write failed");
                            });
                });
    }
}
