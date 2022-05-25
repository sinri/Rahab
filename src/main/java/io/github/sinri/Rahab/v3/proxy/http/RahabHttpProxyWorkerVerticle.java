package io.github.sinri.Rahab.v3.proxy.http;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogLevel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @since 3.0.0
 */
class RahabHttpProxyWorkerVerticle extends KeelVerticle {
    private final String requestID;
    /**
     * 代理通讯
     */
    private final NetSocket proxySocket;
    private final NetClient freedomClient;

    private static final Pattern patternForConnectRequest = Pattern.compile("CONNECT (.+):(\\d+) HTTP/1.1");


    public RahabHttpProxyWorkerVerticle(NetSocket proxySocket, String requestID) {
        this.proxySocket = proxySocket;
        this.requestID = requestID;
        this.freedomClient = Keel.getVertx().createNetClient();
    }

    @Override
    public void start() throws Exception {
        KeelLogger workerLogger = Keel.standaloneLogger("HttpProxyWorker").setCategoryPrefix(requestID);
        setLogger(workerLogger);

        proxyServerConnectHandler();
    }

    private void proxyServerConnectHandler() {
        KeelLogger workerLogger = getLogger();

        workerLogger.notice("建立了一个新的 代理通讯 服务浏览器 " + proxySocket.remoteAddress().toString());

        AtomicBoolean atomicConnectionEstablished = new AtomicBoolean(false);
        AtomicReference<NetSocket> atomicFreedomSocket = new AtomicReference<>();

        proxySocket
                .handler(bufferFromClient -> {
                    workerLogger.info("代理通讯 接收到 浏览器发来的数据包 " + bufferFromClient.length() + " 字节");
                    workerLogger.print(KeelLogLevel.DEBUG, bufferFromClient.toString());

                    if (atomicConnectionEstablished.get()) {
                        // 双边通讯已经完成，双向转发数据即可
                        atomicFreedomSocket.get().write(bufferFromClient)
                                .onSuccess(v -> {
                                    workerLogger.info("代理通讯 通过 自由通讯 转发给目标服务器 成功");
                                })
                                .onFailure(throwable -> {
                                    workerLogger.exception("代理通讯 通过 自由通讯 转发给目标服务器 失败，自由通讯 即将关闭", throwable);
                                    atomicFreedomSocket.get().close();
                                });
                    } else {
                        // 代理通讯 请求建立 自由通讯
                        // 解析CONNECT请求并获取目标服务的地址和端口
                        Matcher matcherForConnectRequest = patternForConnectRequest.matcher(bufferFromClient.toString());
                        String host;
                        int port;
                        if (matcherForConnectRequest.find()) {
                            host = matcherForConnectRequest.group(1);
                            port = Integer.parseInt(matcherForConnectRequest.group(2));
                        } else {
                            workerLogger.warning("代理通讯 无法解析浏览器发来的数据包为CONNECTION请求，代理通讯 即将关闭");
                            workerLogger.debug(bufferFromClient.toString());
                            proxySocket.close();
                            return;
                        }

                        // 使用 中介客户端 发起对目标服务的连接
                        workerLogger.notice("中介客户端 准备连接 目标服务器（" + host + ":" + port + "）");
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
                                        workerLogger.debug("自由通讯 读取到来自目标服务器的数据包，由 代理通讯 转发给浏览器 " + bufferReadFromActualServer.length() + " 字节");
                                        workerLogger.print(KeelLogLevel.DEBUG, bufferReadFromActualServer.toString());
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
                .endHandler(v -> {
                    workerLogger.info("代理通讯 结束，即将关闭");
                    proxySocket.close();
                })
                .exceptionHandler(throwable -> {
                    workerLogger.exception("代理通讯 报错，即将关闭", throwable);
                    proxySocket.close();
                })
                .closeHandler(v -> {
                    workerLogger.notice("代理通讯 关闭");
                    if (atomicFreedomSocket.get() != null) {
                        workerLogger.info("自由通讯 即将关闭");
                        atomicFreedomSocket.get().close()
                                .compose(freedomSocketClosed -> undeployMe());
                    } else {
                        undeployMe();
                    }
                });
    }
}
