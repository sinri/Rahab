package io.github.sinri.Rahab.test.v2;

import io.github.sinri.keel.Keel;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.net.NetServer;

public class EchoServerTest {
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

        NetServer echoServer = Keel.getVertx().createNetServer();

        echoServer.connectHandler(socket -> {
            socket.handler(buffer -> {
                Buffer bufferToEcho = Buffer.buffer();
                bufferToEcho.appendString("Received: ").appendBuffer(buffer);
                socket.write(bufferToEcho);
            });
        }).listen(33333);
    }
}
