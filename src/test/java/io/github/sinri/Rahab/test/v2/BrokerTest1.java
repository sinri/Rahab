package io.github.sinri.Rahab.test.v2;

import io.github.sinri.Rahab.v2.liaison.RahabLiaisonBroker;
import io.github.sinri.Rahab.v2.liaison.RahabLiaisonSource;
import io.github.sinri.Rahab.v2.liaison.RahabLiaisonSourceWorker;
import io.github.sinri.Rahab.v2.liaison.SourceWorkerGenerator;
import io.github.sinri.Rahab.v2.liaison.impl.RahabLiaisonSourceWorkerAsWormhole;
import io.github.sinri.keel.Keel;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class BrokerTest1 {
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

        String brokerHost = "127.0.0.1";
        int brokerPort = 22222;
        String httpProxyHost = "127.0.0.1";
        int httpProxyPort = 33333;

        Future.succeededFuture()
                .compose(v -> new RahabLiaisonBroker()
                        .listen(brokerPort)
                        .onComplete(netServerAsyncResult -> {
                            if (netServerAsyncResult.failed()) {
                                Keel.outputLogger("BrokerTest1").exception("RahabProxyBroker 启动失败", netServerAsyncResult.cause());
                                Keel.getVertx().close();
                            } else {
                                Keel.outputLogger("BrokerTest1").notice("RahabProxyBroker 启动成功 端口 " + brokerPort);
                            }
                        })
                )
                .compose(x -> new RahabLiaisonSource("LiaisonSource")
                        .setSourceWorkerGenerator(new SourceWorkerGenerator() {
                            @Override
                            protected RahabLiaisonSourceWorker generateWorker() {
                                return new RahabLiaisonSourceWorkerAsWormhole(httpProxyHost, httpProxyPort);
                            }
                        })
                        .start(brokerHost, brokerPort)
                        .onComplete(netServerAsyncResult -> {
                            if (netServerAsyncResult.failed()) {
                                Keel.outputLogger("BrokerTest1").exception("RahabLiaisonSource 启动失败", netServerAsyncResult.cause());
                                Keel.getVertx().close();
                            } else {
                                Keel.outputLogger("BrokerTest1").notice("RahabLiaisonSource 启动成功 掮客 地址 " + brokerHost + "端口 " + brokerPort);
                            }
                        })
                );


    }
}
