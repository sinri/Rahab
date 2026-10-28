package io.github.sinri.rahab.v5;

import io.vertx.core.Vertx;
import io.vertx.core.net.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class NetworkTest {
    Vertx vertx;
    NetClient client;
    @BeforeEach void setup() {
        vertx = Vertx.vertx();
        client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000));
    }
    @AfterEach void cleanup() throws Exception { await(vertx.close()); }
    static <T> T await(io.vertx.core.Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
    NetServer echo() throws Exception {
        return await(vertx.createNetServer().connectHandler(socket -> {
            socket.handler(data -> socket.write(data));
            socket.exceptionHandler(e -> socket.close());
        }).listen(0, "127.0.0.1"));
    }
    int proxy(String user, String password) throws Exception {
        return await(vertx.createNetServer().connectHandler(s -> new Socks5Session(vertx, s, client, user, password))
                .listen(0, "127.0.0.1")).actualPort();
    }
    Socket socket(int port) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        return socket;
    }
    byte[] read(Socket socket, int size) throws Exception {
        byte[] data = socket.getInputStream().readNBytes(size);
        assertEquals(size, data.length);
        return data;
    }
    byte[] request(int port) {
        return new byte[]{5,1,0,1,127,0,0,1,(byte)(port >>> 8),(byte)port};
    }
    @Test void anonymousFragmentationAndPipelinedPayload() throws Exception {
        NetServer echo = echo();
        try (Socket socket = socket(proxy(null, null))) {
            byte[] greeting = {5,1,0};
            for (byte b : greeting) { socket.getOutputStream().write(b); socket.getOutputStream().flush(); }
            assertArrayEquals(new byte[]{5,0}, read(socket,2));
            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            wire.write(request(echo.actualPort())); wire.write("hello".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().write(wire.toByteArray());
            assertEquals(0, read(socket,10)[1]);
            assertEquals("hello", new String(read(socket,5), StandardCharsets.UTF_8));
        }
    }
    @Test void failedAuthenticationCannotConnect() throws Exception {
        NetServer echo = echo();
        try (Socket socket = socket(proxy("a", "b"))) {
            socket.getOutputStream().write(new byte[]{5,1,2});
            assertArrayEquals(new byte[]{5,2},read(socket,2));
            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            wire.write(new byte[]{1,1,'a',1,'x'}); wire.write(request(echo.actualPort()));
            socket.getOutputStream().write(wire.toByteArray());
            assertArrayEquals(new byte[]{1,1},read(socket,2));
            assertEquals(-1,socket.getInputStream().read());
        }
    }
    @Test void concurrentAuthenticationIsIndependent() throws Exception {
        int port = proxy("a", "b");
        try (Socket one = socket(port); Socket two = socket(port)) {
            one.getOutputStream().write(new byte[]{5,1,2});
            two.getOutputStream().write(new byte[]{5,1,2});
            assertArrayEquals(new byte[]{5,2},read(one,2));
            assertArrayEquals(new byte[]{5,2},read(two,2));
            one.getOutputStream().write(new byte[]{1,1,'a'});
            two.getOutputStream().write(new byte[]{1,1,'a',1,'x'});
            assertArrayEquals(new byte[]{1,1},read(two,2));
            one.getOutputStream().write(new byte[]{1,'b'});
            assertArrayEquals(new byte[]{1,0},read(one,2));
        }
    }
    @Test void unsignedCredentialLengthsAndHighPort() throws Exception {
        String longUser = "u".repeat(200), longPass = "p".repeat(200);
        NetServer echo = await(vertx.createNetServer().connectHandler(s -> s.handler(b -> s.write(b)))
                .listen(0,"127.0.0.1"));
        // The unsigned port assertion is covered by explicitly binding above 32767.
        if (echo.actualPort() < 32768) {
            await(echo.close());
            for (int port = 49152; ; port++) {
                try { echo = await(vertx.createNetServer().connectHandler(s -> s.handler(b -> s.write(b))).listen(port,"127.0.0.1")); break; }
                catch (Exception e) { if (port == 49252) throw e; }
            }
        }
        try (Socket socket = socket(proxy(longUser,longPass))) {
            socket.getOutputStream().write(new byte[]{5,1,2}); read(socket,2);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(1); bytes.write(200); bytes.write(longUser.getBytes(StandardCharsets.UTF_8));
            bytes.write(200); bytes.write(longPass.getBytes(StandardCharsets.UTF_8));
            bytes.write(request(echo.actualPort())); bytes.write('x');
            socket.getOutputStream().write(bytes.toByteArray());
            assertArrayEquals(new byte[]{1,0},read(socket,2));
            assertEquals(0,read(socket,10)[1]);
            assertEquals('x',socket.getInputStream().read());
        }
    }
    @Test void wormholeBackpressureAndByteIntegrity() throws Exception {
        NetServer echo = echo();
        int port = await(vertx.createNetServer().connectHandler(s -> TcpRelay.connect(s,client,"127.0.0.1",echo.actualPort()))
                .listen(0,"127.0.0.1")).actualPort();
        try (Socket socket = socket(port)) {
            byte[] bytes = new byte[512 * 1024];
            new java.util.Random(7).nextBytes(bytes);
            CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
                try { socket.getOutputStream().write(bytes); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            assertArrayEquals(bytes,read(socket,bytes.length));
            writer.get(5,TimeUnit.SECONDS);
        }
    }
    @Test void targetClosureClosesProxyClient() throws Exception {
        int target = await(vertx.createNetServer().connectHandler(NetSocket::close).listen(0,"127.0.0.1")).actualPort();
        int port = await(vertx.createNetServer().connectHandler(s -> TcpRelay.connect(s,client,"127.0.0.1",target))
                .listen(0,"127.0.0.1")).actualPort();
        try (Socket socket = socket(port)) { assertEquals(-1,socket.getInputStream().read()); }
    }
    @Test void longDomainAndFullyCoalescedHandshake() throws Exception {
        String domain = "a".repeat(60) + "." + "b".repeat(60) + "." + "c".repeat(30) + ".test";
        Vertx resolverVertx = Vertx.vertx(new io.vertx.core.VertxOptions().setAddressResolverOptions(
                new io.vertx.core.dns.AddressResolverOptions().setHostsValue(io.vertx.core.buffer.Buffer.buffer("127.0.0.1 " + domain))));
        try {
            NetClient resolverClient = resolverVertx.createNetClient();
            int port = await(resolverVertx.createNetServer().connectHandler(s ->
                    new Socks5Session(resolverVertx,s,resolverClient,null,null)).listen(0,"127.0.0.1")).actualPort();
            NetServer target = echo();
            try (Socket socket = socket(port)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bytes.write(new byte[]{5,1,0,5,1,0,3,(byte)domain.length()});
                bytes.write(domain.getBytes(StandardCharsets.US_ASCII));
                bytes.write(target.actualPort() >>> 8); bytes.write(target.actualPort()); bytes.write('z');
                socket.getOutputStream().write(bytes.toByteArray());
                assertArrayEquals(new byte[]{5,0},read(socket,2));
                assertEquals(0,read(socket,10)[1]);
                assertEquals('z',socket.getInputStream().read());
            }
        } finally { await(resolverVertx.close()); }
    }
    @Test void fragmentedConnectPreservesEveryByte() throws Exception {
        NetServer target = echo();
        try (Socket socket = socket(proxy(null,null))) {
            socket.getOutputStream().write(new byte[]{5,1,0}); read(socket,2);
            for (byte b : request(target.actualPort())) {
                socket.getOutputStream().write(b); socket.getOutputStream().flush();
                Thread.sleep(5);
            }
            assertEquals(0,read(socket,10)[1]);
            socket.getOutputStream().write('q'); assertEquals('q',socket.getInputStream().read());
        }
    }
    @Test void unsupportedCommandAndAddressAreRejected() throws Exception {
        int port = proxy(null,null);
        for (byte[] request : new byte[][]{{5,2,0,1},{5,1,0,9}}) {
            try (Socket socket = socket(port)) {
                socket.getOutputStream().write(new byte[]{5,1,0}); read(socket,2);
                socket.getOutputStream().write(request);
                assertEquals(request[1] == 2 ? 7 : 8,read(socket,10)[1]);
                assertEquals(-1,socket.getInputStream().read());
            }
        }
    }
    @Test void clientClosureClosesSocksTarget() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        int target = await(vertx.createNetServer().connectHandler(s -> s.closeHandler(v -> closed.countDown()))
                .listen(0,"127.0.0.1")).actualPort();
        try (Socket socket = socket(proxy(null,null))) {
            socket.getOutputStream().write(new byte[]{5,1,0}); read(socket,2);
            socket.getOutputStream().write(request(target)); assertEquals(0,read(socket,10)[1]);
        }
        assertTrue(closed.await(5,TimeUnit.SECONDS));
    }
    @Test void refusedTargetGetsFailureAndClosure() throws Exception {
        int port;
        try (ServerSocket reserved = new ServerSocket(0)) { port = reserved.getLocalPort(); }
        try (Socket socket = socket(proxy(null,null))) {
            socket.getOutputStream().write(new byte[]{5,1,0}); read(socket,2);
            socket.getOutputStream().write(request(port)); assertEquals(5,read(socket,10)[1]);
            assertEquals(-1,socket.getInputStream().read());
        }
    }
    @Test void slowTargetReceivesLargeStreamWithoutLoss() throws Exception {
        byte[] bytes = new byte[2 * 1024 * 1024]; new java.util.Random(13).nextBytes(bytes);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        CompletableFuture<byte[]> completed = new CompletableFuture<>();
        int target = await(vertx.createNetServer().connectHandler(s -> {
            s.pause();
            s.handler(data -> {
                received.writeBytes(data.getBytes());
                if (received.size() == bytes.length) { completed.complete(received.toByteArray()); s.close(); }
                else { s.pause(); vertx.setTimer(2,id -> s.resume()); }
            });
            vertx.setTimer(100,id -> s.resume());
        }).listen(0,"127.0.0.1")).actualPort();
        int port = await(vertx.createNetServer().connectHandler(s -> TcpRelay.connect(s,client,"127.0.0.1",target))
                .listen(0,"127.0.0.1")).actualPort();
        try (Socket socket = socket(port)) {
            socket.getOutputStream().write(bytes);
            assertArrayEquals(bytes,completed.get(10,TimeUnit.SECONDS));
        }
    }

}
