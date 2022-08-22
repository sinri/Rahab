package io.github.sinri.rahab.test.v4;

import io.github.sinri.keel.Keel;
import io.github.sinri.rahab.v4.periscope.PeriscopeLens;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class PeriscopeLensTest {
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

        PeriscopeLens periscopeLens = new PeriscopeLens(
                "127.0.0.1", 20000,
                "127.0.0.1", 20002
        );
        periscopeLens.start();
    }
}
