package io.github.sinri.rahab.v5;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static io.github.sinri.rahab.v5.NetworkTest.await;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class PeriscopeTest {
    Vertx mirror, lens;
    NetClient client;
    int viewerPort, lensPort;
    @BeforeEach void setup() throws Exception {
        mirror = Vertx.vertx(); lens = Vertx.vertx();
        client = lens.createNetClient(new NetClientOptions().setConnectTimeout(1000));
        try (ServerSocket a = new ServerSocket(0); ServerSocket b = new ServerSocket(0)) {
            viewerPort = a.getLocalPort(); lensPort = b.getLocalPort();
        }
        await(Periscope.mirror(mirror,"127.0.0.1",viewerPort,lensPort,"secret"));
    }
    @AfterEach void cleanup() throws Exception { await(lens.close()); await(mirror.close()); }
    Socket socket(int port) throws Exception {
        Socket socket = new Socket("127.0.0.1",port); socket.setSoTimeout(5000); return socket;
    }
    int echo(boolean greeting, AtomicInteger connections) throws Exception {
        return await(lens.createNetServer().connectHandler(target -> {
            connections.incrementAndGet();
            target.closeHandler(v -> connections.decrementAndGet());
            if (greeting) target.write("hello");
            target.handler(data -> target.write(data));
            target.exceptionHandler(e -> target.close());
        }).listen(0,"127.0.0.1")).actualPort();
    }
    void startLens(int target) throws Exception {
        await(Periscope.lens(lens,client,"127.0.0.1",lensPort,"127.0.0.1",target,"secret"));
    }
    void eventually(java.util.function.BooleanSupplier check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!check.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(check.getAsBoolean());
    }
    @Test void multiplexesServerFirstAndClosesTargets() throws Exception {
        AtomicInteger count = new AtomicInteger(); startLens(echo(true,count));
        try (Socket one = socket(viewerPort); Socket two = socket(viewerPort)) {
            assertEquals("hello",new String(one.getInputStream().readNBytes(5),java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("hello",new String(two.getInputStream().readNBytes(5),java.nio.charset.StandardCharsets.UTF_8));
            one.getOutputStream().write(new byte[]{1,2,3}); two.getOutputStream().write(new byte[]{9,8});
            assertArrayEquals(new byte[]{1,2,3},one.getInputStream().readNBytes(3));
            assertArrayEquals(new byte[]{9,8},two.getInputStream().readNBytes(2));
            assertEquals(2,count.get());
        }
        eventually(() -> count.get() == 0);
    }
    @Test void unauthenticatedPeerCannotBecomeLens() throws Exception {
        try (Socket bad = socket(lensPort)) {
            bad.getOutputStream().write(new Photon(Photon.AUTH,"",Buffer.buffer("wrong")).encode().getBytes());
            assertEquals(-1,bad.getInputStream().read());
        }
        AtomicInteger count = new AtomicInteger(); startLens(echo(false,count));
        try (Socket viewer = socket(viewerPort); Socket attacker = socket(lensPort)) {
            attacker.getOutputStream().write(new Photon(Photon.AUTH,"",Buffer.buffer("secret")).encode().getBytes());
            assertEquals(-1,attacker.getInputStream().read());
            viewer.getOutputStream().write(42); assertEquals(42,viewer.getInputStream().read());
        }
    }
    @Test void tunnelDisconnectClosesViewersAndTargets() throws Exception {
        AtomicInteger count = new AtomicInteger(); startLens(echo(true,count));
        try (Socket viewer = socket(viewerPort)) {
            assertEquals(5,viewer.getInputStream().readNBytes(5).length);
            await(client.close());
            assertEquals(-1,viewer.getInputStream().read());
            eventually(() -> count.get() == 0);
        }
    }
    @Test void malformedFrameClosesTunnelAndDoesNotBreakListener() throws Exception {
        try (Socket bad = socket(lensPort)) {
            bad.getOutputStream().write(Buffer.buffer().appendInt(Integer.MAX_VALUE).getBytes());
            assertEquals(-1,bad.getInputStream().read());
        }
        startLens(echo(false,new AtomicInteger()));
        try (Socket viewer = socket(viewerPort)) {
            viewer.getOutputStream().write(12); assertEquals(12,viewer.getInputStream().read());
        }
    }
    @Test void missingLensClosesViewer() throws Exception {
        try (Socket viewer = socket(viewerPort)) { assertEquals(-1,viewer.getInputStream().read()); }
    }
    @Test void failedTargetConnectClosesViewer() throws Exception {
        int closed;
        try (ServerSocket reserved = new ServerSocket(0)) { closed = reserved.getLocalPort(); }
        startLens(closed);
        try (Socket viewer = socket(viewerPort)) { assertEquals(-1,viewer.getInputStream().read()); }
    }
    @Test void reconnectsAfterMirrorRestartWithoutReusingOldSessions() throws Exception {
        AtomicInteger count = new AtomicInteger(); startLens(echo(true,count));
        try (Socket old = socket(viewerPort)) {
            assertEquals(5,old.getInputStream().readNBytes(5).length);
            await(mirror.close());
            assertEquals(-1,old.getInputStream().read());
            eventually(() -> count.get() == 0);
            mirror = Vertx.vertx();
            await(Periscope.mirror(mirror,"127.0.0.1",viewerPort,lensPort,"secret"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            boolean connected = false;
            while (System.nanoTime() < deadline) {
                try (Socket next = socket(viewerPort)) {
                    if (next.getInputStream().readNBytes(5).length == 5) {
                        next.getOutputStream().write(77); assertEquals(77,next.getInputStream().read());
                        connected = true; break;
                    }
                }
                Thread.sleep(50);
            }
            assertTrue(connected,"Lens did not reconnect");
        }
    }
    @Test void heartbeatRoundTripKeepsTunnelUsable() throws Exception {
        AtomicInteger count = new AtomicInteger();
        startLens(echo(false,count));
        Thread.sleep(5500);
        assertEquals(0,count.get(),"Heartbeats must not open target connections");
        try (Socket viewer = socket(viewerPort)) {
            viewer.getOutputStream().write(66); assertEquals(66,viewer.getInputStream().read());
        }
    }

}
