package io.github.sinri.rahab.v4.periscope;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.controlflow.FutureUntil;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.web.tcp.KeelAbstractSocketWrapper;
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
    private KeelAbstractSocketWrapper socketWrapperFromLens;

    private final Map<String, KeelAbstractSocketWrapper> socketWrapperToViewerMap;

    public PeriscopeMirror(int port) {
        this.port = port;
        this.logger = Keel.standaloneLogger("PeriscopeMirror").setCategoryPrefix("SERVER");

        this.socketWrapperToViewerMap = new ConcurrentHashMap<>();
    }

    public KeelLogger getLogger() {
        return logger;
    }

    public void run() {
        Keel.getVertx().createNetServer()
                .connectHandler(socket -> SocketAgent.build(socket, this))
                .exceptionHandler(logger::exception)
                .listen(this.port);
    }

    static class SocketAgent extends KeelAbstractSocketWrapper {
        private final PeriscopeMirror mirror;
        private SocketType socketType = SocketType.INIT;
        private final PhotonProcessor photonProcessor;

        @Override
        protected Future<Void> whenBufferComes(Buffer buffer) {
            if (socketType == SocketType.INIT) {
                if (buffer.toString().equals("[PeriscopeLensReport]")) {
                    // FROM LENS: register
                    mirror.socketWrapperFromLens = this;
                    socketType = SocketType.LENS;
                    return Future.succeededFuture();
                } else {
                    // FROM VIEWER
                    socketType = SocketType.VIEWER;
                    mirror.socketWrapperToViewerMap.put(getSocketID(), this);
                }
            }

            if (socketType == SocketType.LENS) {
                // LENS
                return handleBufferFromLens(buffer);
            } else {
                // VIEWER
                if (mirror.socketWrapperFromLens == null) {
                    getLogger().error("mirror.socketFromLens is null");
                    return this.close();
                }
                Photon photon = Photon.create(getSocketID(), buffer);
                return mirror.socketWrapperFromLens.write(photon.toBuffer());
            }
        }

        @Override
        protected void whenClose() {
            super.whenClose();
            if (socketType == SocketType.LENS) {
                mirror.socketWrapperFromLens = null;
            } else if (socketType == SocketType.VIEWER) {
                mirror.socketWrapperToViewerMap.remove(getSocketID());
            }
        }

        private SocketAgent(NetSocket socket, PeriscopeMirror mirror) {
            super(socket, UUID.randomUUID().toString().replace("-", ""));
            this.mirror = mirror;
            this.photonProcessor = new PhotonProcessor();
            setLogger(Keel.standaloneLogger("PeriscopeMirror").setCategoryPrefix(getSocketID()));

            getLogger().notice("VIEWER FROM " + socket.remoteAddress().hostAddress() + ":" + socket.remoteAddress().port());
        }

        public static SocketAgent build(NetSocket socket, PeriscopeMirror mirror) {
            return new SocketAgent(socket, mirror);
        }

        public Future<Void> handleBufferFromLens(Buffer bufferFromLens) {
            photonProcessor.accept(bufferFromLens);
            Queue<Photon> photonQueue = photonProcessor.getPieceQueue();

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
                            return mirror.socketWrapperFromLens.write(
                                            Photon.create("PeriscopeMirror", Buffer.buffer().appendString("PONG"))
                                                    .toBuffer()
                                    )
                                    .compose(v -> {
                                        return Future.succeededFuture(false);
                                    });

                        }
                    }

                    KeelAbstractSocketWrapper socketWrapperToViewer = mirror.socketWrapperToViewerMap.get(viewerIdentity);
                    if (socketWrapperToViewer == null) {
                        getLogger().error("SOCKET TO VIEWER LOST");
                        return Future.succeededFuture(false);
                    }
                    return socketWrapperToViewer.write(photon.getContentBuffer())
                            .compose(v -> {
                                return Future.succeededFuture(false);
                            });

                }
            });
        }

        enum SocketType {
            INIT, LENS, VIEWER
        }
    }
}
