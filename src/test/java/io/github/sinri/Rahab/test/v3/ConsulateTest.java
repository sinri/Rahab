package io.github.sinri.Rahab.test.v3;

import io.github.sinri.Rahab.test.RahabTestKit;
import io.github.sinri.Rahab.v3.consulate.ConsulateClient;
import io.github.sinri.Rahab.v3.consulate.ConsulateServer;
import io.github.sinri.Rahab.v3.proxy.socks5.RahabSocks5ProxyVerticle;
import io.github.sinri.Rahab.v3.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.github.sinri.Rahab.v3.proxy.socks5.auth.impl.RahabSocks5AuthMethod00;
import io.github.sinri.keel.Keel;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

import java.util.HashSet;
import java.util.Set;

public class ConsulateTest {
    private static final int socks5port = 10000;
    private static final int consulateServerPort = 443;
    private static final int consulateClientPort = 10002;

    public static void main(String[] args) {
        RahabTestKit.init();
        CompositeFuture.all(
                        socks5(),
                        consulateServer(),
                        consulateClient()
                )
                .onComplete(compositeFutureAsyncResult -> {
                    if (compositeFutureAsyncResult.failed()) {
                        Keel.outputLogger("ConsulateTest").exception("ANY ERROR", compositeFutureAsyncResult.cause());
                    } else {
                        Keel.outputLogger("ConsulateTest").info("ALL DONE");
                    }
                });
    }

    private static Future<String> socks5() {
        RahabSocks5AuthMethod00 rahabSocks5AuthMethod00 = new RahabSocks5AuthMethod00();

        Set<RahabSocks5AuthMethod> authMethodSet = new HashSet<>();
        authMethodSet.add(rahabSocks5AuthMethod00);

        RahabSocks5ProxyVerticle rahabSocks5ProxyVerticle = new RahabSocks5ProxyVerticle(socks5port, authMethodSet, 10);
        return rahabSocks5ProxyVerticle
                .deployMe()
                .onComplete(stringAsyncResult -> {
                    if (stringAsyncResult.failed()) {
                        Keel.outputLogger("ConsulateTest").exception("Socks5 ERROR", stringAsyncResult.cause());
                    } else {
                        Keel.outputLogger("ConsulateTest").info("Socks5 START " + stringAsyncResult.result() + " listen on " + socks5port);
                    }
                });
    }

    private static Future<String> consulateServer() {
        String jksPath = "~/domain.jks";
        String jksPasswordFile = "~/jks-password.txt";

        return Keel.getVertx().fileSystem()
                .readFile(jksPasswordFile)
                .compose(buffer -> {
                    return Future.succeededFuture(buffer.toString());
                })
                .compose(jksPassword -> {
                    return new ConsulateServer("/consulate", consulateServerPort, socks5port, "127.0.0.1", jksPath, jksPassword)
                            .deployMe()
                            .onComplete(stringAsyncResult -> {
                                if (stringAsyncResult.failed()) {
                                    Keel.outputLogger("ConsulateTest").exception("ConsulateServer ERROR", stringAsyncResult.cause());
                                } else {
                                    Keel.outputLogger("ConsulateTest").info("ConsulateServer START " + stringAsyncResult.result() + " listen on " + consulateServerPort + " -> 127.0.0.1:" + socks5port);
                                }
                            });
                });
    }

    private static Future<String> consulateClient() {
        return new ConsulateClient(
                consulateClientPort,
                "sample.com",
                consulateServerPort,
                "/consulate"
        )
                .deployMe()
                .onComplete(stringAsyncResult -> {
                    if (stringAsyncResult.failed()) {
                        Keel.outputLogger("ConsulateTest").exception("ConsulateClient ERROR", stringAsyncResult.cause());
                    } else {
                        Keel.outputLogger("ConsulateTest").info("ConsulateClient START " + stringAsyncResult.result() + " listen on " + consulateClientPort);
                    }
                });
    }
}
