package io.github.sinri.Rahab.test.v2;

import io.github.sinri.Rahab.v2.WormholeProxy;
import io.github.sinri.Rahab.v2.transform.HttpRequestTransformPair;
import io.github.sinri.keel.Keel;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class WormholeProxyRemoteTest {
    /**
     * listen on (22222 ->) 44444 -> 33333
     * @param args
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

        new WormholeProxy("127.0.0.1",33333)
                .encodingPair(new HttpRequestTransformPair("fake.com"),false)
                .listen(44444);
    }
}
