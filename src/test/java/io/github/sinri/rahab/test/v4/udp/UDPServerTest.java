package io.github.sinri.rahab.test.v4.udp;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.net.SocketAddress;

import java.util.Objects;
import java.util.function.BiConsumer;

public class UDPServerTest {
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

        UDPServer udpServerA = new UDPServer(19999);
        UDPServer udpServerB = new UDPServer(19998);

        udpServerA.listen();
        udpServerB.listen();

        udpServerB.setDatagramSocketConsumer((sender, buffer) -> {
            udpServerB.send(
                    Buffer.buffer()
                            .appendString(sender.hostAddress() + ":" + sender.port())
                    ,
                    sender.port(),
                    sender.hostAddress()
            );
        });

        udpServerA.send(Buffer.buffer("FROM A"), 19998, "127.0.0.1");
        Keel.getVertx().createDatagramSocket()
                .send(Buffer.buffer("FROM X"), 19998, "127.0.0.1");
    }

    static class UDPServer {
        private final int port;
        private final DatagramSocket udpServer;
        private final KeelLogger logger;
        private BiConsumer<SocketAddress, Buffer> datagramSocketConsumer = (sender, buffer) -> {
            // do nothing
        };

        public UDPServer(int port) {
            this.port = port;
            this.udpServer = Keel.getVertx().createDatagramSocket();
            this.logger = Keel.outputLogger("server");
        }

        public UDPServer setDatagramSocketConsumer(BiConsumer<SocketAddress, Buffer> datagramSocketConsumer) {
            Objects.requireNonNull(datagramSocketConsumer);
            this.datagramSocketConsumer = datagramSocketConsumer;
            return this;
        }

        public Future<Object> listen() {
            return udpServer.listen(port, "0.0.0.0")
                    .compose(datagramSocket -> {
                        datagramSocket.handler(datagramPacket -> {
                                    SocketAddress sender = datagramPacket.sender();
                                    Buffer data = datagramPacket.data();

                                    logger.info("from " + sender.hostAddress() + ":" + sender.port() + " received: " + data);
                                    this.datagramSocketConsumer.accept(sender, data);
                                })
                                .endHandler(end -> {
                                    logger.info("read end");
                                })
                                .exceptionHandler(throwable -> {
                                    logger.exception(throwable);
                                });
                        return Future.succeededFuture();
                    });
        }

        public Future<Void> send(Buffer buffer, int targetPort, String targetAddress) {
            return udpServer.send(buffer, targetPort, targetAddress)
                    .onSuccess(done -> {
                        logger.info("sent to " + targetAddress + ":" + targetPort + " data: " + buffer);
                    })
                    .onFailure(throwable -> {
                        logger.exception("failed to send to " + targetAddress + ":" + targetPort, throwable);
                    });
        }

        public Future<Void> close() {
            return udpServer.close();
        }
    }
}
