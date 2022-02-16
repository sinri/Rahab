package io.github.sinri.Rahab.v2;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP Proxy
 * {浏览器} ←[代理通讯]→ {代理服务器 ↔ 中介客户端} ←[自由通讯]→ {目标服务器}
 */
public class RahabHttpProxy {
    /**
     * 代理服务器
     */
    private final NetServer proxyServer;

    protected KeelLogger proxyLogger = Keel.logger("RahabHttpProxy");
    private static final Pattern patternForConnectRequest = Pattern.compile("CONNECT (.+):(\\d+) HTTP/1.1");

    public RahabHttpProxy() {
        this.proxyServer = Keel.getVertx().createNetServer()
                .connectHandler(this::proxyServerConnectHandler)
                .exceptionHandler(throwable -> {
                    proxyLogger.exception("代理服务器 出现故障", throwable);
                });
    }

    public Future<NetServer> listen(int proxyPort) {
        return this.proxyServer.listen(proxyPort);
    }

    /**
     * @param proxySocket 代理通讯
     */
    private void proxyServerConnectHandler(NetSocket proxySocket) {
        String requestID = UUID.randomUUID().toString().replace("-","");
        KeelLogger workerLogger = Keel.logger("HttpProxyWorker").setCategoryPrefix(requestID);

        workerLogger.notice("建立了一个新的 代理通讯 服务浏览器 " + proxySocket.remoteAddress().toString());

        AtomicBoolean atomicConnectionEstablished = new AtomicBoolean(false);
        AtomicReference<NetSocket> atomicFreedomSocket = new AtomicReference<>();

        proxySocket
                .handler(buffer -> {
                    workerLogger.debug("代理通讯 接收到 浏览器发来的数据包: " + buffer);
                    if (atomicConnectionEstablished.get()) {
                        // 双边通讯已经完成，双向转发数据即可
                        atomicFreedomSocket.get().write(buffer)
                                .onSuccess(v -> {
                                    workerLogger.info("代理通讯 通过 自由通讯 转发给目标服务器 成功");
                                })
                                .onFailure(throwable -> {
                                    workerLogger.exception("代理通讯 通过 自由通讯 转发给目标服务器 失败，自由通讯 即将关闭", throwable);
                                    atomicFreedomSocket.get().close();
                                });
                    } else {
                        // 代理通讯 请求建立 自由通讯

                        Matcher matcherForConnectRequest = patternForConnectRequest.matcher(buffer.toString());
                        String host;
                        int port;
                        if (matcherForConnectRequest.find()) {
                            host = matcherForConnectRequest.group(1);
                            port = Integer.parseInt(matcherForConnectRequest.group(2));
                        } else {
                            workerLogger.warning("代理通讯 无法解析浏览器发来的数据包为CONNECTION请求，代理通讯 即将关闭");
                            proxySocket.close();
                            return;
                        }

                        // 中介客户端
                        NetClient freedomClient = Keel.getVertx().createNetClient();
                        workerLogger.notice("中介客户端 完成初始化 面向 目标服务器（" + host + ":" + port + "）");
                        freedomClient.connect(port, host, netSocketAsyncResult -> {
                            if (netSocketAsyncResult.failed()) {
                                workerLogger.exception("中介客户端 无法连接 目标服务器，代理通讯 即将关闭", netSocketAsyncResult.cause());
                                proxySocket.close();
                                return;
                            }

                            NetSocket freedomSocket = netSocketAsyncResult.result();

                            atomicFreedomSocket.set(freedomSocket);
                            workerLogger.notice("中介客户端 已建立 自由通讯 连接到目标服务器 " + atomicFreedomSocket.get().remoteAddress());

                            freedomSocket
                                    .handler(bufferReadFromActualServer -> {
                                        workerLogger.debug("自由通讯 读取到来自目标服务器的数据包，由 代理通讯 转发给浏览器: " + bufferReadFromActualServer);
                                        proxySocket.write(bufferReadFromActualServer);
                                    })
                                    .exceptionHandler(throwable -> {
                                        workerLogger.exception("自由通讯 报告错误，即将关闭", throwable);
                                        freedomSocket.close();
                                    })
                                    .closeHandler(v -> {
                                        workerLogger.notice("自由通讯 关闭，代理通讯 即将关闭");
                                        proxySocket.close();
                                    });

                            proxySocket.write("HTTP/1.1 200 Connection Established\r\n\r\n")
                                    .onSuccess(v -> {
                                        workerLogger.info("代理通讯 成功向浏览器发送 Connection Established 数据包, 准备开始代理服务");
                                        atomicConnectionEstablished.set(true);
                                    })
                                    .onFailure(throwable -> {
                                        workerLogger.exception("代理通讯 未能向浏览器发送 Connection Established 数据包, 代理通讯 即将关闭", throwable);
                                        proxySocket.close();
                                    });
                        });
                    }
                })
                .exceptionHandler(throwable -> {
                    workerLogger.exception("代理通讯 报错，即将关闭", throwable);
                    proxySocket.close();
                })
                .closeHandler(v -> {
                    workerLogger.notice("代理通讯 关闭");
                    if (atomicFreedomSocket.get() != null) {
                        workerLogger.info("自由通讯 即将关闭");
                        atomicFreedomSocket.get().close();
                    }
                });
    }

}
