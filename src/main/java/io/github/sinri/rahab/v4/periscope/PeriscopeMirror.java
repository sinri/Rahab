package io.github.sinri.rahab.v4.periscope;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.controlflow.FutureUntil;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.servant.sisiodosi.KeelSisiodosi;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PeriscopeMirror {
    private final int port;
    private final KeelLogger logger;
    private NetSocket socketFromLens;

    private final Map<String, NetSocket> socketToViewerMap;
    private final KeelSisiodosi sisiodosi;

    public PeriscopeMirror(int port) {
        this.port = port;
        this.logger = Keel.standaloneLogger("PeriscopeMirror").setCategoryPrefix("SERVER");

        this.socketToViewerMap = new ConcurrentHashMap<>();
        this.sisiodosi = new KeelSisiodosi(this.getClass().getName());
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void run() {
        Keel.getVertx().createNetServer()
                .connectHandler(socket -> {
                    SocketAgent socketAgent = new SocketAgent(this, socket);
                })
                .exceptionHandler(throwable -> {
                    logger.exception(throwable);
                })
                .listen(this.port);
    }

    static class SocketAgent {
        private final PeriscopeMirror mirror;
        private final NetSocket socket;
        private final String identity;
        private SocketType socketType = SocketType.INIT;
        private final PhotonProcessor photonProcessor;

        private final KeelLogger logger;

        private KeelLogger getLogger() {
            return logger;
        }

        public SocketAgent(PeriscopeMirror mirror, NetSocket socket) {
            this.mirror = mirror;
            this.socket = socket;
            this.identity = UUID.randomUUID().toString().replace("-", "");
            this.photonProcessor = new PhotonProcessor();
            this.logger = Keel.standaloneLogger("PeriscopeMirror").setCategoryPrefix(identity);

            this.logger.notice("VIEWER FROM " + socket.remoteAddress().hostAddress() + ":" + socket.remoteAddress().port());

            this.socket
                    .handler(buffer -> {
                        if (socketType == SocketType.INIT) {
                            if (buffer.toString().equals("[PeriscopeLensReport]")) {
                                // FROM LENS: register
                                mirror.socketFromLens = socket;
                                socketType = SocketType.LENS;
                                return;
                            } else {
                                // FROM VIEWER
                                socketType = SocketType.VIEWER;
                                mirror.socketToViewerMap.put(identity, socket);
                            }
                        }

                        if (socketType == SocketType.LENS) {
                            // LENS
                            String lockName = "LockForPeriscopeMirrorWithSocket-" + identity;
                            Keel.getVertx().sharedData().getLock(lockName)
                                    .compose(lock -> {
                                        mirror.sisiodosi.drop(v -> {
                                            return handleBufferFromLens(buffer);
                                        });
                                        lock.release();
                                        return Future.succeededFuture();
                                    }, throwable -> {
                                        getLogger().exception("lock acquire failed", throwable);
                                        return socket.close();
                                    });
                        } else {
                            // VIEWER
                            if (mirror.socketFromLens == null) {
                                getLogger().error("mirror.socketFromLens is null");
                                socket.close();
                                return;
                            }
                            Photon photon = Photon.create(identity, buffer);
                            mirror.socketFromLens.write(photon.toBuffer());
                            if (mirror.socketFromLens.writeQueueFull()) {
                                mirror.socketFromLens.pause();
                            }
                        }
                    })
                    .endHandler(end -> {
                        mirror.logger.info("END");
                    })
                    .drainHandler(drain -> {
                        mirror.logger.info("drain");
                        socket.resume();
                    })
                    .closeHandler(close -> {
                        mirror.logger.info("close");
                        if (socketType == SocketType.LENS) {
                            mirror.socketFromLens = null;
                        } else if (socketType == SocketType.VIEWER) {
                            mirror.socketToViewerMap.remove(identity);
                        }
                    });

        }

        public Future<Void> handleBufferFromLens(Buffer bufferFromLens) {
            photonProcessor.receive(bufferFromLens);
            Queue<Photon> photonQueue = photonProcessor.getPhotonQueue();

            return FutureUntil.call(new Supplier<Future<Boolean>>() {
                @Override
                public Future<Boolean> get() {
                    Photon photon = photonQueue.poll();
                    if (photon == null) {
                        return Future.succeededFuture(true);
                    }
                    String viewerIdentity = photon.getIdentity();

                    if (Objects.equals("PeriscopeLens", viewerIdentity)) {
                        if (Objects.equals(photon.toBuffer().toString(), "PING")) {
                            // just ping!
                            mirror.socketFromLens.write(
                                    Photon.create("PeriscopeMirror", Buffer.buffer().appendString("PONG"))
                                            .toBuffer()
                            );
                            if (mirror.socketFromLens.writeQueueFull()) {
                                mirror.socketFromLens.pause();
                            }
                            return Future.succeededFuture(false);
                        }
                    }

                    NetSocket socketToViewer = mirror.socketToViewerMap.get(viewerIdentity);
                    if (socketToViewer == null) {
                        getLogger().error("SOCKET TO VIEWER LOST");
                        return Future.succeededFuture(false);
                    }
                    socketToViewer.write(photon.getContentBuffer());
                    if (socketToViewer.writeQueueFull()) {
                        socketToViewer.pause();
                    }
                    return Future.succeededFuture(false);
                }
            });
        }

        enum SocketType {
            INIT, LENS, VIEWER
        }
    }
}
