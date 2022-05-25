package io.github.sinri.Rahab.test.v3;

import io.github.sinri.Rahab.v3.wormhole.WormholeVerticle;
import io.github.sinri.keel.Keel;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class WormholeTest {
    /**
     * listen on 1086 -> 7090
     *
     */
    public static void main(String[] args) {
        Keel.loadPropertiesFromFile("config.properties");
        Keel.initializeVertx(new VertxOptions().setAddressResolverOptions(
                        new AddressResolverOptions()
                                .addServer("119.29.29.29")
                                .addServer("223.5.5.5")
                                .addServer("1.1.1.1")
                                .addServer("114.114.114.114")
                                .addServer("8.8.8.8")
                )
        );

        WormholeVerticle wormholeVerticle = new WormholeVerticle("本地虫洞 1086 -> 7090", 1086, "127.0.0.1", 7090);
        wormholeVerticle.deployMe();


    }
}
