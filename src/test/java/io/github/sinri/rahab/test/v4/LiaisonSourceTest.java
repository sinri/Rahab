package io.github.sinri.rahab.test.v4;

import io.github.sinri.keel.Keel;
import io.github.sinri.rahab.v4.liaison.meta.SourceOptions;
import io.github.sinri.rahab.v4.liaison.source.RahabLiaisonSource;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

public class LiaisonSourceTest {
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

        RahabLiaisonSource source = new RahabLiaisonSource(new SourceOptions()
                .setSourceName("Local")
                .setBrokerHost("127.0.0.1")
                .setBrokerPort(20001)
                .setProxyHost("127.0.0.1")
                .setProxyPort(20002)
        );
        source.run();
    }
}
