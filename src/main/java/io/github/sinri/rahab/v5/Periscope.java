package io.github.sinri.rahab.v5;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;

/** Dedicated viewer and authenticated tunnel listeners avoid sniffing arbitrary TCP payloads. */
final class Periscope {
    private Periscope() {}

    static Future<NetServer> mirror(Vertx vertx, String host, int port, int lensPort, String token) {
        // Both listeners and their connection maps must belong to the same event-loop context.
        Promise<NetServer> result = Promise.promise();
        vertx.getOrCreateContext().runOnContext(v -> mirrorOnContext(vertx, host, port, lensPort, token).onComplete(result));
        return result.future();
    }

    private static Future<NetServer> mirrorOnContext(Vertx vertx, String host, int port, int lensPort, String token) {
        Map<String, NetSocket> viewers = new HashMap<>();
        Tunnel[] active = {null};
        NetServer listener = vertx.createNetServer();
        listener.connectHandler(socket -> {
            Tunnel candidate = new Tunnel(vertx, socket);
            candidate.receiver = frame -> {
                if (active[0] != candidate) {
                    if (frame.type() != Photon.AUTH || !frame.id().isEmpty() || active[0] != null || !MessageDigest.isEqual(
                            token.getBytes(StandardCharsets.UTF_8), frame.data().getBytes()))
                        return Future.failedFuture("Tunnel authentication rejected");
                    active[0] = candidate;
                    return candidate.send(Photon.READY, "", Buffer.buffer());
                }
                if (frame.type() == Photon.PING) return candidate.send(Photon.PONG, "", Buffer.buffer());
                if (frame.type() == Photon.PONG) return Future.succeededFuture();
                NetSocket viewer = viewers.get(frame.id());
                if (frame.type() == Photon.CLOSE) {
                    if (viewer != null) viewer.close();
                    return Future.succeededFuture();
                }
                if (frame.type() != Photon.DATA) return Future.failedFuture("Unexpected frame");
                return viewer == null ? Future.succeededFuture() : viewer.write(frame.data()).recover(e -> viewer.close());
            };
            candidate.onClose = () -> {
                if (active[0] == candidate) {
                    active[0] = null;
                    new ArrayList<>(viewers.values()).forEach(NetSocket::close);
                    viewers.clear();
                }
            };
        });
        return listener.listen(lensPort, host).compose(bound -> {
            NetServer server = vertx.createNetServer();
            server.connectHandler(viewer -> {
                Tunnel tunnel = active[0];
                if (tunnel == null || viewers.size() >= 4096) { viewer.close(); return; }
                String id = UUID.randomUUID().toString();
                viewers.put(id, viewer);
                tunnel.addSource(viewer);
                tunnel.send(Photon.OPEN, id, Buffer.buffer());
                viewer.handler(data -> tunnel.send(Photon.DATA, id, data));
                viewer.exceptionHandler(e -> viewer.close());
                viewer.closeHandler(v -> {
                    viewers.remove(id);
                    tunnel.sources.remove(viewer);
                    tunnel.send(Photon.CLOSE, id, Buffer.buffer());
                });
            });
            return server.listen(port, host).onFailure(e -> listener.close());
        });
    }

    static Future<Void> lens(Vertx vertx, NetClient client, String host, int port,
                             String targetHost, int targetPort, String token) {
        Lens lens = new Lens(vertx, client, host, port, targetHost, targetPort, token);
        return lens.connect();
    }

    private static final class Lens {
        final Vertx vertx;
        final NetClient client;
        final String host, targetHost, token;
        final int port, targetPort;
        Lens(Vertx vertx, NetClient client, String host, int port, String targetHost, int targetPort, String token) {
            this.vertx = vertx; this.client = client; this.host = host; this.port = port;
            this.targetHost = targetHost; this.targetPort = targetPort; this.token = token;
        }
        Future<Void> connect() {
            return client.connect(port, host).compose(socket -> {
                Promise<Void> ready = Promise.promise();
                Tunnel tunnel = new Tunnel(vertx, socket);
                Map<String, NetSocket> targets = new HashMap<>();
                boolean[] authenticated = {false};
                tunnel.receiver = frame -> {
                    if (!authenticated[0]) {
                        if (frame.type() != Photon.READY) return Future.failedFuture("Expected READY");
                        authenticated[0] = true;
                        ready.tryComplete();
                        return Future.succeededFuture();
                    }
                    return switch (frame.type()) {
                        case Photon.PONG -> Future.succeededFuture();
                        case Photon.PING -> tunnel.send(Photon.PONG, "", Buffer.buffer());
                        case Photon.OPEN -> {
                            if (targets.containsKey(frame.id()) || frame.id().isEmpty() || targets.size() >= 4096)
                                yield Future.failedFuture("Invalid OPEN");
                            // Serial dispatch ensures subsequent DATA cannot overtake this connect.
                            yield client.connect(targetPort, targetHost).compose(target -> {
                                if (tunnel.closed) { target.close(); return Future.succeededFuture(); }
                                targets.put(frame.id(), target);
                                tunnel.addSource(target);
                                target.handler(data -> tunnel.send(Photon.DATA, frame.id(), data));
                                target.exceptionHandler(e -> target.close());
                                target.closeHandler(v -> {
                                    targets.remove(frame.id(), target);
                                    tunnel.sources.remove(target);
                                    tunnel.send(Photon.CLOSE, frame.id(), Buffer.buffer());
                                });
                                return Future.<Void>succeededFuture();
                            }).recover(e -> tunnel.send(Photon.CLOSE, frame.id(), Buffer.buffer()));
                        }
                        case Photon.DATA -> {
                            NetSocket target = targets.get(frame.id());
                            yield target == null ? Future.succeededFuture()
                                    : target.write(frame.data()).recover(e -> target.close());
                        }
                        case Photon.CLOSE -> {
                            NetSocket target = targets.remove(frame.id());
                            if (target != null) target.close();
                            yield Future.succeededFuture();
                        }
                        default -> Future.failedFuture("Unexpected frame");
                    };
                };
                tunnel.onClose = () -> {
                    ready.tryFail("Tunnel closed before READY");
                    new ArrayList<>(targets.values()).forEach(NetSocket::close);
                    targets.clear();
                    if (authenticated[0]) retry();
                };
                tunnel.send(Photon.AUTH, "", Buffer.buffer(token));
                return ready.future();
            });
        }
        void retry() {
            // During Vert.x shutdown the context rejects new timers; no reconnect survives shutdown.
            try { vertx.setTimer(1000, id -> connect().onFailure(e -> retry())); }
            catch (IllegalStateException ignored) { }
        }
    }

    private static final class Tunnel {
        static final int MAX_PENDING = 4 * 1024 * 1024;
        final NetSocket socket;
        final Set<NetSocket> sources = new HashSet<>();
        final Queue<Photon> incoming = new ArrayDeque<>();
        Function<Photon, Future<Void>> receiver;
        Runnable onClose = () -> {};
        boolean closed, processing;
        int inboundBytes, outboundBytes;
        long lastSeen = System.nanoTime();
        Tunnel(Vertx vertx, NetSocket socket) {
            this.socket = socket;
            socket.setWriteQueueMaxSize(64 * 1024);
            Photon.Decoder decoder = new Photon.Decoder();
            socket.handler(data -> {
                socket.pause();
                try {
                    decoder.accept(data, frame -> {
                        if (closed) return;
                        inboundBytes += frame.data().length() + frame.id().length() * 3 + 7;
                        if (inboundBytes > MAX_PENDING) throw new IllegalArgumentException("Inbound queue exceeded");
                        incoming.add(frame);
                    });
                    process(vertx);
                } catch (RuntimeException e) { socket.close(); }
            });
            socket.exceptionHandler(e -> socket.close());
            socket.drainHandler(v -> sources.forEach(NetSocket::resume));
            long timer = vertx.setPeriodic(5000, id -> {
                if (System.nanoTime() - lastSeen > 20_000_000_000L) socket.close();
                else send(Photon.PING, "", Buffer.buffer());
            });
            socket.closeHandler(v -> {
                closed = true;
                vertx.cancelTimer(timer);
                incoming.clear();
                onClose.run();
            });
        }
        void process(Vertx vertx) {
            if (closed || processing) return;
            Photon frame = incoming.poll();
            if (frame == null) { socket.resume(); return; }
            processing = true;
            lastSeen = System.nanoTime();
            try {
                receiver.apply(frame).onComplete(result -> {
                    inboundBytes -= frame.data().length() + frame.id().length() * 3 + 7;
                    processing = false;
                    if (result.failed()) socket.close();
                    else vertx.runOnContext(v -> process(vertx));
                });
            } catch (RuntimeException e) { socket.close(); }
        }
        void addSource(NetSocket source) {
            sources.add(source);
            if (socket.writeQueueFull()) source.pause();
        }
        Future<Void> send(int type, String id, Buffer data) {
            if (closed) return Future.succeededFuture();
            Buffer wire = new Photon(type, id, data).encode();
            if (outboundBytes + wire.length() > MAX_PENDING) {
                socket.close();
                return Future.failedFuture("Outbound queue exceeded");
            }
            outboundBytes += wire.length();
            Future<Void> write = socket.write(wire);
            write.onComplete(v -> { outboundBytes -= wire.length(); if (v.failed()) socket.close(); });
            if (socket.writeQueueFull()) sources.forEach(NetSocket::pause);
            return write;
        }
    }
}
