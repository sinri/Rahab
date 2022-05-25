package io.github.sinri.Rahab.test.v2;

import io.github.sinri.Rahab.v3.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.github.sinri.Rahab.v3.proxy.socks5.auth.impl.RahabSocks5AuthMethod00;
import io.github.sinri.Rahab.v3.proxy.socks5.auth.impl.RahabSocks5AuthMethod02;
import io.github.sinri.keel.Keel;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

import java.util.HashSet;
import java.util.Set;

public class Socks5ProxyTest {
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

        RahabSocks5AuthMethod00 rahabSocks5AuthMethod00 = new RahabSocks5AuthMethod00();
        RahabSocks5AuthMethod02 rahabSocks5AuthMethod02 = new RahabSocks5AuthMethod02(new RahabSocks5AuthMethod02.UsernamePasswordVerifier() {
            @Override
            public Future<Boolean> verify(String username, String password) {
                if (username.equals("u") && password.equals("p"))
                    return Future.succeededFuture(true);
                return Future.succeededFuture(false);
            }
        });

        Set<RahabSocks5AuthMethod> authMethodSet = new HashSet<>();
        authMethodSet.add(rahabSocks5AuthMethod00);
//        authMethodSet.add(rahabSocks5AuthMethod02);

        new RahabSocks5Proxy(authMethodSet)
                .listen(7090)
                .onFailure(throwable -> {
                    Keel.outputLogger("Socks5ProxyTest").exception("LISTEN FAILED", throwable);
                })
                .onSuccess(x -> {
                    Keel.outputLogger("Socks5ProxyTest").notice("LISTEN STARTED on 7090");
                });
    }
}
