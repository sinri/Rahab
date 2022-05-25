package io.github.sinri.Rahab.test.v2;

import io.github.sinri.Rahab.v3.wormhole.transform.impl.http.client.TransformerFromHttpRequestToRaw;
import io.github.sinri.Rahab.v3.wormhole.transform.impl.http.client.TransformerFromRawToHttpRequest;
import io.github.sinri.Rahab.v3.wormhole.transform.impl.http.server.TransformerFromHttpResponseToRaw;
import io.github.sinri.Rahab.v3.wormhole.transform.impl.http.server.TransformerFromRawToHttpResponse;
import io.github.sinri.keel.Keel;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class Test1 {
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

        int portOfRahabHttpProxy = 9041;
        int portOfWormholeProxyAsRemote = 9042;
        int portOfWormholeProxyAsLocal = 9043;
        int portOfWormholeProxyAsRaw = 22222;
        Future.succeededFuture()
                .compose(v -> {
                    System.out.println("GO RahabHttpProxy listen on " + portOfRahabHttpProxy);
                    return new RahabHttpProxy()
                            .listen(portOfRahabHttpProxy)
                            .compose(server -> {
                                System.out.println("OK RahabHttpProxy listen on " + portOfRahabHttpProxy);
                                return Future.succeededFuture();
                            });
                })
                .compose(v -> {
                    System.out.println("GO WormholeProxy 远程 listen on " + portOfWormholeProxyAsRemote + " -> " + portOfRahabHttpProxy + " ...");
                    return new Wormhole("远程", "127.0.0.1", portOfRahabHttpProxy)
                            .setTransformerForDataFromRemote(new TransformerFromRawToHttpResponse())
                            .setTransformerForDataFromLocal(new TransformerFromHttpRequestToRaw())
                            .listen(portOfWormholeProxyAsRemote)
                            .compose(server -> {
                                System.out.println("OK WormholeProxy 远程 listen on " + portOfWormholeProxyAsRemote + " -> " + portOfRahabHttpProxy);
                                return Future.succeededFuture();
                            });
                })
                .compose(v -> {
                    System.out.println("GO WormholeProxy 本地 listen on " + portOfWormholeProxyAsLocal + " -> " + portOfWormholeProxyAsRemote + " ...");
                    return new Wormhole("本地", "127.0.0.1", portOfWormholeProxyAsRemote)
                            .setTransformerForDataFromRemote(new TransformerFromHttpResponseToRaw())
                            .setTransformerForDataFromLocal(new TransformerFromRawToHttpRequest("fake.com"))
                            .listen(portOfWormholeProxyAsLocal)
                            .compose(server -> {
                                System.out.println("OK WormholeProxy 本地 listen on " + portOfWormholeProxyAsLocal + " -> " + portOfWormholeProxyAsRemote);
                                return Future.succeededFuture();
                            });
                })
                .compose(v -> {
                    System.out.println("GO WormholeProxy 虫洞 listen on " + portOfWormholeProxyAsRaw + " -> " + portOfWormholeProxyAsLocal + " ...");
                    return new Wormhole("虫洞", "127.0.0.1", portOfWormholeProxyAsLocal)
                            //.encodingPair(new HttpRequestTransformPair("fake.com"),false)
                            .listen(portOfWormholeProxyAsRaw)
                            .compose(server -> {
                                System.out.println("OK WormholeProxy 虫洞 listen on " + portOfWormholeProxyAsRaw + " -> " + portOfWormholeProxyAsLocal);
                                return Future.succeededFuture();
                            });
                })
                .onFailure(throwable -> {
                    throwable.printStackTrace();
//                    Keel.getVertx().close();
                });
    }
}
