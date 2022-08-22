package io.github.sinri.rahab.v4.liaison.broker;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4.liaison.entity.LiaisonEnvelope;
import io.github.sinri.rahab.v4.liaison.entity.SocketWrapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.util.Map;
import java.util.UUID;

public class RahabLiaisonBrokerHandlerForTerminal {
    private final Map<String, SocketWrapper> sources;
    private final Map<String, SocketWrapper> terminals;
    private final KeelLogger logger;
    private final String currentTerminalID;
    private NetSocket currentTerminalSocket;

    public RahabLiaisonBrokerHandlerForTerminal(
            Map<String, SocketWrapper> sources,
            Map<String, SocketWrapper> terminals
    ) {
        this.sources = sources;
        this.terminals = terminals;
        this.logger = Keel.outputLogger("终端处理");
        this.currentTerminalID = UUID.randomUUID().toString();
        this.logger.setContentPrefix("终端(" + this.currentTerminalID + ")");
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void handle(NetSocket socket) {
        currentTerminalSocket = socket;

        registerTerminal();

        currentTerminalSocket
                .handler(bufferFromTerminalToBroker -> {
                    try {
                        SocketWrapper proxySocketWrapper = getProxySocketWrapper();
                        if (proxySocketWrapper == null) {
                            getLogger().error("当前无源可用，关闭终端通信");
                            currentTerminalSocket.close();
                            return;
                        }
                        getLogger().debug("自终端读取到 " + bufferFromTerminalToBroker.length() + " 字节 ");
                        getLogger().buffer(bufferFromTerminalToBroker);
                        Buffer bufferFromBrokerToSource = LiaisonEnvelope.buildForBrokerToTransfer(currentTerminalID, bufferFromTerminalToBroker).toBuffer();

                        proxySocketWrapper.write(bufferFromBrokerToSource)
                                .compose(written -> {
                                    getLogger().info("转发给源成功");
                                    return Future.succeededFuture();
                                }, throwable -> {
                                    getLogger().exception("转发给源失败，关闭终端通信", throwable);
                                    declareTerminalDead();
                                    return Future.succeededFuture();
                                });
                    } catch (Throwable throwable) {
                        getLogger().exception("处理来自终端的数据时抛出异常", throwable);
                    }
                })
                .endHandler(ended -> {
                    getLogger().info("终端通信 读到结尾了");
                })
                .drainHandler(drain -> {
                    getLogger().info("终端通信 drain");
                    currentTerminalSocket.resume();
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("终端连接失败", throwable);
                    declareTerminalDead();
                })
                .closeHandler(closed -> {
                    getLogger().info("终端通信关闭");
                    terminals.remove(this.currentTerminalID);
                    getLogger().info("自终端映射表中移除终端名 " + this.currentTerminalID);
                });
    }

    private void registerTerminal() {
        SocketWrapper socketWrapper = new SocketWrapper();
        socketWrapper.update(this.currentTerminalID, this.currentTerminalSocket);

        SocketWrapper existed = terminals.get(this.currentTerminalID);
        if (existed != null) {
            getLogger().warning("终端名 [" + this.currentTerminalID + "] 对应的通信已存在，需要先关闭");
            existed.close();
        }
        terminals.put(this.currentTerminalID, socketWrapper);
        getLogger().info("终端注册完毕");
    }

    private void declareTerminalDead() {
        SocketWrapper existed = terminals.get(this.currentTerminalID);
        if (existed != null) {
            existed.close();
        }
    }

    private SocketWrapper getProxySocketWrapper() {
        for (String x : sources.keySet()) {
            return sources.get(x);
        }
        return null;
    }
}
