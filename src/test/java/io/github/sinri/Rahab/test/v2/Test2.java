package io.github.sinri.Rahab.test.v2;

import io.github.sinri.Rahab.v2.RahabHttpProxy;
import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pump;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test2 {
    /*
     * 1.[B->P] CONNECT imququ.com:443 HTTP/1.1
     * 1.[B<-P] HTTP/1.1 200 Connection Established
     * 2.[B->P->S] ...
     * 2.[B<-P<-S] ...
     */

    public static void main(String[] args) {
        Keel.loadPropertiesFromFile("config.properties");
        Keel.initializeVertx(
                new VertxOptions().
                        setAddressResolverOptions(
                                new AddressResolverOptions()
                                        .addServer("119.29.29.29")
                                        .addServer("223.5.5.5")
                                        .addServer("1.1.1.1")
                                        .addServer("114.114.114.114")
                                        .addServer("8.8.8.8")
                        )
        );

        //rawImpl();

        new RahabHttpProxy().listen(22222);
    }

    private static void rawImpl() {
        KeelLogger proxyLogger = Keel.logger("HttpProxy");

        Pattern patternForConnectRequest = Pattern.compile("CONNECT (.+):(\\d+) HTTP/1.1");

        NetServer proxy = Keel.getVertx().createNetServer();
        proxy.connectHandler(proxySocket -> {
            String requestID = UUID.randomUUID().toString();
            KeelLogger workerLogger = Keel.logger("HttpProxyWorker").setCategoryPrefix(requestID);

            workerLogger.info("新连接 来自 浏览器 " + proxySocket.remoteAddress().toString());

            AtomicBoolean connectionEstablished = new AtomicBoolean(false);
            AtomicReference<NetSocket> actualServerSocket = new AtomicReference<>();

            proxySocket.handler(buffer -> {
                        workerLogger.debug("浏览器发来的数据包: " + buffer);
                        if (!connectionEstablished.get()) {
                            // read header for host
                            Matcher matcherForConnectRequest = patternForConnectRequest.matcher(buffer.toString());
                            String host;
                            int port;
                            if (matcherForConnectRequest.find()) {
                                host = matcherForConnectRequest.group(1);
                                port = Integer.parseInt(matcherForConnectRequest.group(2));
                            } else {
                                workerLogger.error("浏览器应该发来CONNECTION请求，但无法解析出目标服务器。关闭与此浏览器相关的代理通道。");
                                proxySocket.close();
                                return;
                            }

                            NetClient client = Keel.getVertx().createNetClient();
                            workerLogger.info("初始化一个面向目标服务器（" + host + ":" + port + "）的中介客户端");
                            client.connect(port, host, netSocketAsyncResult -> {
                                if (netSocketAsyncResult.failed()) {
                                    workerLogger.exception("中介客户端无法连接目标服务器", netSocketAsyncResult.cause());
                                    proxySocket.close();
                                    return;
                                }

                                actualServerSocket.set(netSocketAsyncResult.result());
                                workerLogger.info("中介客户端已建立 自由通讯 连接到目标服务器 " + actualServerSocket.get().remoteAddress());

                                actualServerSocket.get()
                                        .handler(bufferReadFromActualServer -> {
                                            workerLogger.debug("自由通讯 读取到来自目标服务器的数据包，由代理转发给浏览器: " + bufferReadFromActualServer);
                                            proxySocket.write(bufferReadFromActualServer);
                                        })
                                        .exceptionHandler(throwable -> {
                                            workerLogger.exception("自由通讯 报告错误，即将关闭", throwable);
                                            actualServerSocket.get().close();
                                        })
                                        .closeHandler(v -> {
                                            workerLogger.info("自由通讯 关闭");
                                            proxySocket.close();
                                        });

                                proxySocket.closeHandler(v -> {
                                    workerLogger.info("代理通道 关闭");
                                    actualServerSocket.get().close();
                                });

                                proxySocket.write("HTTP/1.1 200 Connection Established\r\n\r\n")
                                        .onSuccess(v -> {
//                                        Pump.pump(actualServerSocket.get(), proxySocket);
//                                        Pump.pump(proxySocket, actualServerSocket.get());
                                            workerLogger.info("代理通道 成功向浏览器发送 Connection Established 数据包, 准备开始代理服务");
                                            connectionEstablished.set(true);
                                        })
                                        .onFailure(throwable -> {
                                            workerLogger.exception("代理通道未能向浏览器发送 Connection Established 数据包", throwable);
                                            actualServerSocket.get().close();
                                        });
                            });

                        } else {
                            // else: pump to actual server
                            workerLogger.debug("代理通道 自 浏览器 读取到数据包，准备通过 自由通讯 转发给目标服务器: " + buffer);
                            actualServerSocket.get().write(buffer);
                        }
                    })
                    .exceptionHandler(throwable -> {
                        workerLogger.exception("代理通道 报错，即将关闭", throwable);
                        proxySocket.close();
                    })
                    .closeHandler(v -> {
                        workerLogger.error("代理通道 关闭");
                    });
        });
        proxy.exceptionHandler(throwable -> {
            proxyLogger.exception("代理工具 报错", throwable);
        });
        proxy.listen(22222);

    }
}
