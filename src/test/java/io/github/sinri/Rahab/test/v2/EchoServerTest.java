package io.github.sinri.Rahab.test.v2;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;

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

        NetClient netClient = Keel.getVertx().createNetClient();

        for (var i = 0; i < 10; i++) {
            KeelLogger logger = Keel.outputLogger("Client-" + i);

            int finalI = i;
            netClient.connect(33333, "127.0.0.1", netSocketAsyncResult -> {
                if (netSocketAsyncResult.failed()) {
                    logger.exception(netSocketAsyncResult.cause());
                    return;
                }

                NetSocket clientSocket = netSocketAsyncResult.result();
                clientSocket.handler(buffer -> {
                            logger.info("Response: " + buffer);
                        })
                        .exceptionHandler(throwable -> {
                            logger.exception(throwable);
                        })
                        .closeHandler(v -> {
                            logger.notice("close");
                        });
                clientSocket.write("I am Client " + finalI)
                        .onComplete(voidAsyncResult -> {
                            if (voidAsyncResult.failed()) {
                                logger.exception("write failed", voidAsyncResult.cause());
                            } else {
                                logger.info("written");
                            }
                        });
            });
        }
    }
}
