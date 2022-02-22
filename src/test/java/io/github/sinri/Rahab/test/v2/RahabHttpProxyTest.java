package io.github.sinri.Rahab.test.v2;

import io.github.sinri.Rahab.v2.proxy.http.RahabHttpProxy;
import io.github.sinri.keel.Keel;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class RahabHttpProxyTest {
    /*
     * 1.[B->P] CONNECT imququ.com:443 HTTP/1.1
     * 1.[B<-P] HTTP/1.1 200 Connection Established
     * 2.[B->P->S] ...
     * 2.[B<-P<-S] ...
     */

    /**
     * listen on 33333
     * @param args
     */
    public static void main(String[] args) {
        Keel.loadPropertiesFromFile("config.properties");
        Keel.initializeVertx(
                new VertxOptions()
                        .setWorkerPoolSize(32)
                        .setAddressResolverOptions(
                                new AddressResolverOptions()
                                        .addServer("119.29.29.29")
                                        .addServer("223.5.5.5")
                                        .addServer("1.1.1.1")
                                        .addServer("114.114.114.114")
                                        .addServer("8.8.8.8")
                        )
        );

        //rawImpl();

        int portOfRahabHttpProxy = 33333;
        new RahabHttpProxy().listen(portOfRahabHttpProxy)
                .compose(server -> {
                    System.out.println("OK RahabHttpProxy listen on " + portOfRahabHttpProxy);
                    return Future.succeededFuture();
                })
                .onFailure(throwable -> {
                    System.err.println(throwable.getMessage());
                });
    }
}
