package io.github.sinri.rahab.test.v4;

import io.github.sinri.keel.Keel;
import io.github.sinri.rahab.v4.proxy.socks5.RahabSocks5Proxy;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class Socks5ProxyTest {
    public static void main(String[] args) {
        Keel.loadPropertiesFromFile("config.properties");
        Keel.initializeVertx(new VertxOptions()
                .setEventLoopPoolSize(128)
                .setAddressResolverOptions(
                        new AddressResolverOptions()
                                .addServer("119.29.29.29")
                                .addServer("223.5.5.5")
                                .addServer("1.1.1.1")
                                .addServer("114.114.114.114")
                                .addServer("8.8.8.8")
                )
        );

        RahabSocks5Proxy rahabSocks5Proxy = new RahabSocks5Proxy(20002);
//        RahabSocks5Proxy rahabSocks5Proxy = new RahabSocks5Proxy(
//                20002,
//                RahabSocks5AuthMethod.createBasicAuthMap(new RahabSocks5AuthMethod02.UsernamePasswordVerifier() {
//                    @Override
//                    public Future<Boolean> verify(String username, String password) {
//                        return Future.succeededFuture(Objects.equals(username, password));
//                    }
//                })
//        );
        rahabSocks5Proxy.run();
    }
}