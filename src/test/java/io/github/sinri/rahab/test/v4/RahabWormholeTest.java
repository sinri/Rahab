package io.github.sinri.rahab.test.v4;

import io.github.sinri.keel.Keel;
import io.github.sinri.rahab.v4.wormhole.RahabWormhole;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class RahabWormholeTest {
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

        RahabWormhole rahabWormhole = new RahabWormhole(20005, "127.0.0.1", 20002);
        rahabWormhole.run();
    }
}
