package io.github.sinri.rahab.v4.liaison.broker;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.controlflow.FutureForEach;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4.liaison.entity.LiaisonEnvelope;
import io.github.sinri.rahab.v4.liaison.entity.LiaisonEnvelopeProcessor;
import io.github.sinri.rahab.v4.liaison.entity.SocketWrapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.util.List;
import java.util.Map;

public class RahabLiaisonBrokerHandlerForSource {
    private final Map<String, SocketWrapper> sources;
    private final Map<String, SocketWrapper> terminals;
    private final KeelLogger logger;
    private final LiaisonEnvelopeProcessor proxyPost;

    private String currentProxyName;
    private NetSocket currentProxySocket;

    public RahabLiaisonBrokerHandlerForSource(
            Map<String, SocketWrapper> sources,
            Map<String, SocketWrapper> terminals,
            LiaisonEnvelopeProcessor proxyPost
    ) {
        this.sources = sources;
        this.terminals = terminals;
        this.logger = Keel.outputLogger("源处理");
        this.proxyPost = proxyPost;
        this.currentProxyName = null;

        this.logger.setContentPrefix("对源(?)的处理");
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void handle(NetSocket socket) {
        currentProxySocket = socket;
        currentProxySocket
                .handler(bufferFromSource -> {
                    getLogger().debug("从源读取到 " + bufferFromSource.length() + " 字节");
                    getLogger().buffer(bufferFromSource);
                    List<LiaisonEnvelope> liaisonEnvelopes = this.proxyPost.parseAll(bufferFromSource);
                    FutureForEach.call(liaisonEnvelopes, this::handleEnvelope)
                            .onComplete(asyncResult -> {
                                if (asyncResult.failed()) {
                                    getLogger().exception("处理信封们失败，关闭与源的通信", asyncResult.cause());
                                    currentProxySocket.close();
                                } else {
                                    getLogger().info("处理信封们成功");
                                }
                            });
                })
                .endHandler(ended -> {
                    getLogger().info("源 读完");
                })
                .drainHandler(drain -> {
                    getLogger().info("源 drain");
                    currentProxySocket.resume();
                })
                .exceptionHandler(throwable -> {
                    getLogger().exception("从源读取失败", throwable);
                    // 此时并不知道发过来的是个什么鬼，所以也不能作出相应的处理
                    socket.close();
                })
                .closeHandler(closed -> {
                    getLogger().notice("与源的通信关闭");
                    if (currentProxyName != null) {
                        sources.remove(currentProxyName);
                    }
                });
    }

    private Future<Void> handleEnvelope(LiaisonEnvelope liaisonEnvelope) {
        String contentHolder = liaisonEnvelope.getContentHolder();
        Buffer contentBuffer = liaisonEnvelope.getContentBuffer();

        getLogger().debug("contentHolder: " + contentHolder);
        getLogger().buffer(contentBuffer);

        // REGISTER: R[PROXY-NAME] EMPTY
        // TRANSFER: T[TERMINAL-NAME] BUFFER
        // UNREGISTER: U[PROXY-NAME] EMPTY

        if (contentHolder.length() <= 1) {
            return Future.failedFuture("envelope content holder length <= 1");
        }

        String action = contentHolder.substring(0, 1);
        String terminalName = contentHolder.substring(1);

        if (this.currentProxyName == null) {// not registered, may register
            if (action.equals("R")) {
                getLogger().notice("识别为 注册操作 源 " + terminalName);
//                JsonObject content = new JsonObject(contentBuffer);
//                String username = content.getString("username");
//                String password = content.getString("password");
//                declareNewProxy(name, username, password);
                declareNewProxy(terminalName);

                LiaisonEnvelope resp = new LiaisonEnvelope(Buffer.buffer("REGISTERED"), this.currentProxyName);

                return this.currentProxySocket.write(resp.toBuffer())
                        .compose(written -> {
                                    getLogger().info("buffer registered and responded to source");
                                    if (this.currentProxySocket.writeQueueFull()) {
                                        getLogger().warning("currentProxySocket PAUSE");
                                        this.currentProxySocket.pause();
                                    }
                                    return Future.succeededFuture();
                                },
                                throwable -> {
                                    getLogger().exception("buffer registered but failed to respond to source", throwable);
                                    if (this.currentProxySocket.writeQueueFull()) {
                                        getLogger().warning("currentProxySocket PAUSE");
                                        this.currentProxySocket.pause();
                                    }
                                    return Future.failedFuture(throwable);
                                });
            } else {
                return Future.failedFuture("ILLEGAL, DECLARE DEAD");
            }
        } else {// registered
            if (action.equals("T")) {// transfer
                return transfer(terminalName, contentBuffer);
            } else if (action.equals("U")) { // unregister
                return Future.failedFuture("UNREGISTER, DECLARE DEAD");
            } else {
                return Future.failedFuture("ILLEGAL, DECLARE DEAD");
            }
        }
    }

    private void declareNewProxy(String name) {
        SocketWrapper socketWrapper = new SocketWrapper();
        socketWrapper.update(name, currentProxySocket);
        sources.put(name, socketWrapper);
        this.currentProxyName = name;
        getLogger().setContentPrefix("源(" + name + ")");
    }

    private Future<Void> transfer(String terminalName, Buffer contentBuffer) {
        SocketWrapper terminal = this.terminals.get(terminalName);
        if (terminal == null) {
            getLogger().warning("无法找到终端 [" + terminalName + "] 忽略此包");
            return Future.succeededFuture();
        }

        if (contentBuffer.length() == 0) {
            getLogger().error("源要求关闭与终端 [" + terminalName + "] 的通信");
            terminal.close();
            return Future.succeededFuture();
        } else {
            return terminal.write(contentBuffer)
                    .compose(v -> {
                        getLogger().info("自源 [" + this.currentProxyName + "] 发送到终端 [" + terminalName + "] " + contentBuffer.length() + " 字节 成功");
                        return Future.succeededFuture();
                    }, throwable -> {
                        getLogger().exception(KeelLogLevel.WARNING, "自源 [" + this.currentProxyName + "] 发送到终端 [" + terminalName + "] " + contentBuffer.length() + " 字节 失败", throwable);
                        terminal.close();
                        terminals.remove(terminalName);
                        return Future.succeededFuture();
                    });
        }

    }
}
