package io.github.sinri.rahab.test.v4;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4.liaison.broker.RahabLiaisonBroker;
import io.github.sinri.rahab.v4.liaison.meta.BrokerOptions;
import io.github.sinri.rahab.v4.liaison.meta.SourceOptions;
import io.github.sinri.rahab.v4.liaison.source.RahabLiaisonSource;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;

public class LiaisonMixTest {
    public static void main(String[] args) {
        Keel.loadPropertiesFromFile("config.properties");
        Keel.initializeVertx(new VertxOptions()
                .setAddressResolverOptions(
                        new AddressResolverOptions()
                                .addServer("119.29.29.29")
                                .addServer("223.5.5.5")
                                .addServer("1.1.1.1")
                                .addServer("114.114.114.114")
                                .addServer("8.8.8.8")
                )
        );

        Keel.getVertx().createNetServer()
                .connectHandler(socket -> {
                    KeelLogger logger = Keel.outputLogger("server");
                    socket.handler(buffer -> {
                        logger.debug("read buffer " + buffer.length() + " bytes");
                        logger.buffer(buffer);

                        int anInt = buffer.getInt(0);

                        socket.write(Buffer.buffer().appendInt(anInt + 1));
                    });
                })
                .listen(30002);

        RahabLiaisonBroker broker = new RahabLiaisonBroker(new BrokerOptions()
                .setProxyRegistrationPort(30001)
                .setTerminalServicePort(30000)
        );
        broker.run();

        RahabLiaisonSource source = new RahabLiaisonSource(new SourceOptions()
                .setSourceName("Local")
                .setBrokerHost("127.0.0.1")
                .setBrokerPort(30001)
                .setProxyHost("127.0.0.1")
                .setProxyPort(30002)
        );
        source.run();

        Keel.getVertx().createNetClient()
                .connect(30000, "127.0.0.1")
                .compose(socket -> {
                    KeelLogger logger = Keel.outputLogger("client");
                    socket.handler(buffer -> {
                        logger.debug("read buffer " + buffer.length() + " bytes");
                        logger.buffer(buffer);

                        int anInt = buffer.getInt(0);

                        socket.write(Buffer.buffer().appendInt(anInt + 1));
                    });
                    return socket.write(Buffer.buffer().appendInt(1));
                });
    }
}
