package io.github.sinri.Rahab.test.v2;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test1 {
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

        Pattern patternForHost = Pattern.compile("^Host:\\s+(.+)\\s*$", Pattern.MULTILINE);
        Pattern patternForContentLength = Pattern.compile("^Content-Length:\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        KeelLogger rahabLogger = Keel.logger("rahab");

        NetServer netServer = Keel.getVertx().createNetServer(new NetServerOptions().setPort(22222));
        netServer.connectHandler(socket -> {
            String requestID = UUID.randomUUID().toString();
            KeelLogger workerLogger = Keel.logger("rahab-worker/" + requestID);
            rahabLogger.info("proxy create worker for new request [" + requestID + "] from " + socket.remoteAddress().toString());
            socket.exceptionHandler(throwable -> {
                workerLogger.exception("worker met exception", throwable);
            });
            AtomicReference<Buffer> tempBuffer = new AtomicReference<>(Buffer.buffer());
            AtomicReference<Buffer> headerBuffer = new AtomicReference<>(Buffer.buffer());
            AtomicReference<String> host = new AtomicReference<>();
            AtomicReference<Long> bodyLength = new AtomicReference<>((long) 0);
            socket.handler(buffer -> {
                tempBuffer.get().appendBuffer(buffer);
                workerLogger.debug("worker read part: " + buffer.toString());
                if (headerBuffer.get().length() == 0) {
                    if (buffer.toString().endsWith("\r\n")) {
                        headerBuffer.get().appendBuffer(tempBuffer.get());
                        String headerString = headerBuffer.toString();
                        workerLogger.debug("worker read entire headers: " + headerString);
                        // check host
                        Matcher matcherForHost = patternForHost.matcher(headerString);
                        if (matcherForHost.find()) {
                            host.set(matcherForHost.group(1));
                            workerLogger.info("Host: " + host);
                        } else {
                            throw new RuntimeException("Host not found in headers");
                        }
                        // check method
                        if (headerBuffer.toString().startsWith("POST")) {
                            Matcher matcherForContentLength = patternForContentLength.matcher(headerString);
                            if (matcherForContentLength.find()) {
                                bodyLength.set(Long.valueOf(matcherForContentLength.group(1)));
                            } else {
                                throw new RuntimeException("Content-Length not found in headers");
                            }
                            // wait for body reading
                            tempBuffer.set(Buffer.buffer());
                        } else {
                            // no request body to read, just send
                            pump(host.get(), headerBuffer.get(), workerLogger,socket);
                            tempBuffer.set(Buffer.buffer());
                            headerBuffer.set(Buffer.buffer());
                        }
                    }
                    // else: read more headers
                } else {
                    // headers already read
                    if (tempBuffer.get().length() >= bodyLength.get()) {
                        // all body read!
                        workerLogger.info("worker read all body: " + tempBuffer.get().toString());
                        // no more request body to read, just send
                        pump(
                                host.get(),
                                Buffer.buffer()
                                        .appendBuffer(headerBuffer.get())
                                        .appendBuffer(tempBuffer.get()),
                                workerLogger,
                                socket
                        );
                        tempBuffer.set(Buffer.buffer());
                        headerBuffer.set(Buffer.buffer());
                    }
                }
            });
            socket.endHandler(v -> {
                workerLogger.debug("worker stop reading");
            });
            socket.closeHandler(v -> {
                workerLogger.info("worker closed");
            });
        });
        netServer.exceptionHandler(throwable -> {
            rahabLogger.exception("proxy met exception", throwable);
        });

        netServer.listen(22222);
    }

    private static void pump(String rawHost, Buffer requestBuffer, KeelLogger logger, NetSocket proxySocket) {
        String host;
        int port;
        if (rawHost.contains(":")) {
            String[] split = rawHost.split(":");
            host = split[0];
            port = Integer.parseInt(split[1]);
        } else {
            host = rawHost;
            port = 80;
        }
//        DnsClient dnsClient = Keel.getVertx().createDnsClient();
//        dnsClient.lookup4(host)
//                .onFailure(throwable -> {
//                    logger.exception("DNS lookup failed for " + host, throwable);
//                })
//                .compose(ip -> {
//                    logger.notice("DNS lookup " + host + " -> " + ip);
//                    return Future.succeededFuture();
//                });

        NetClient netClient = Keel.getVertx().createNetClient();
        netClient.connect(port,host,netSocketAsyncResult -> {
            if(netSocketAsyncResult.failed()){
                logger.exception("worker failed to connect to the actual server",netSocketAsyncResult.cause());
                return;
            }
            NetSocket socket = netSocketAsyncResult.result();

            socket.handler(buffer -> {
                proxySocket.write(buffer);
            });
            socket.endHandler(v->{
                logger.info("worker read all from the actual server, to close");
                socket.close();
            });
            socket.exceptionHandler(throwable -> {
                logger.exception("worker failed in IO with the actual server",throwable);
            });
            socket.closeHandler(v->{
                logger.info("worker closed connection with the actual server");
            });

            socket.write(requestBuffer);
        })
        ;
    }
}
