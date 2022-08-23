package io.github.sinri.rahab.v4.periscope;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.controlflow.FutureUntil;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.web.socket.KeelAbstractSocketWrapper;
import io.github.sinri.keel.web.socket.KeelBasicSocketWrapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PeriscopeLens {
    private final NetClient netClient;
    private final String mirrorHost;
    private final int mirrorPort;
    private final String targetHost;
    private final int targetPort;

    private KeelLogger logger;

    private MirrorSocketWrapper mirrorSocketWrapper;
    private final Map<String, KeelAbstractSocketWrapper> socketWrapperToTargetMap;

    public PeriscopeLens(String mirrorHost, int mirrorPort, String targetHost, int targetPort) {
        this.netClient = Keel.getVertx().createNetClient();

        this.mirrorPort = mirrorPort;
        this.mirrorHost = mirrorHost;
        this.targetHost = targetHost;
        this.targetPort = targetPort;

        this.logger = Keel.standaloneLogger("PeriscopeLens");
        this.socketWrapperToTargetMap = new ConcurrentHashMap<>();
    }

    public PeriscopeLens setLogger(KeelLogger logger) {
        this.logger = logger;
        return this;
    }

    public void run() {
        this.netClient.connect(mirrorPort, mirrorHost)
                .compose(socket -> {
                    return Future.succeededFuture()
                            .compose(v -> {
                                if (this.mirrorSocketWrapper != null) {
                                    return this.mirrorSocketWrapper.close()
                                            .compose(closed -> {
                                                this.mirrorSocketWrapper = null;
                                                return Future.succeededFuture();
                                            });
                                } else {
                                    return Future.succeededFuture();
                                }
                            })
                            .compose(v -> {
                                this.mirrorSocketWrapper = MirrorSocketWrapper.build(socket, this);

                                return mirrorSocketWrapper.write("[PeriscopeLensReport]")
                                        .onSuccess(written -> Keel.getVertx().setTimer(10_000L, timer -> ping()));
                            });
                })
                .recover(throwable -> {
                    this.logger.exception("CONNECT FAILED", throwable);
                    return Keel.getVertx().close();
                });
    }

    private void ping() {
        Buffer buffer = Photon.create("PeriscopeLens", Buffer.buffer().appendString("PING"))
                .toBuffer();
        this.mirrorSocketWrapper.write(buffer)
                .onFailure(throwable -> {
                    logger.debug("PING SENDING FAILED, TO RESTART LATER");
                    Keel.getVertx().setTimer(10_000L, timer -> run());
                })
                .onSuccess(done -> {
                    logger.debug("PING SENT");
                    Keel.getVertx().setTimer(10_000L, timer -> ping());
                });
    }


    private Future<KeelAbstractSocketWrapper> getSocketToTargetMap(String identity) {
        KeelAbstractSocketWrapper socketWrapper = this.socketWrapperToTargetMap.get(identity);
        if (socketWrapper != null) {
            return Future.succeededFuture(socketWrapper);
        }
        return this.netClient.connect(this.targetPort, this.targetHost)
                .compose(socketCreatedToTarget -> {
                    KeelBasicSocketWrapper socketWrapperCreated = new KeelBasicSocketWrapper(socketCreatedToTarget)
                            .setIncomingBufferProcessor(buffer -> {
                                Photon photon = Photon.create(Buffer.buffer(identity), buffer);
                                return this.mirrorSocketWrapper.write(photon.toBuffer());
                            })
                            .setCloseHandler(closed -> {
                                this.socketWrapperToTargetMap.remove(identity);
                            });

                    this.socketWrapperToTargetMap.put(identity, socketWrapperCreated);
                    return Future.succeededFuture(socketWrapperCreated);
                });
    }

    static class MirrorSocketWrapper extends KeelAbstractSocketWrapper {

        private final PeriscopeLens lens;
        private final PhotonProcessor photonProcessor;

        private MirrorSocketWrapper(NetSocket socket, PeriscopeLens lens) {
            super(socket);
            this.lens = lens;
            this.photonProcessor = new PhotonProcessor();
        }

        public static MirrorSocketWrapper build(NetSocket socket, PeriscopeLens lens) {
            return new MirrorSocketWrapper(socket, lens);
        }

        @Override
        protected Future<Void> whenBufferComes(Buffer buffer) {
            return this.handleBufferFromMirror(buffer);
        }

        /**
         * THREAD SAFE NEEDED!
         * RUN IN SISIODOSI.
         */
        private Future<Void> handleBufferFromMirror(Buffer bufferFromMirror) {
            this.photonProcessor.accept(bufferFromMirror);
            Queue<Photon> photonQueue = this.photonProcessor.getPieceQueue();
            return FutureUntil.call(
                    new Supplier<Future<Boolean>>() {
                        @Override
                        public Future<Boolean> get() {
                            Photon photon = photonQueue.poll();
                            if (photon == null) {
                                return Future.succeededFuture(true);
                            }
                            return Future.succeededFuture()
                                    .compose(v -> handlePhoton(photon))
                                    .compose(handled -> {
                                        return Future.succeededFuture(false);
                                    }, throwable -> {
                                        lens.logger.exception(throwable);
                                        return Future.succeededFuture(false);
                                    });
                        }
                    }
            );
        }

        private Future<Void> handlePhoton(Photon photon) {
            // 1. seek/create the socket to target of terminal
            String identity = photon.getIdentityBuffer().toString();
            Buffer contentBuffer = photon.getContentBuffer();

            if (Objects.equals("PeriscopeMirror", identity)) {
                if (Objects.equals("PONG", contentBuffer.toString())) {
                    lens.logger.debug("PONG received");
                    return Future.succeededFuture();
                }
            }

            return lens.getSocketToTargetMap(identity)
                    .compose(socketWrapper -> {
                        // 2. send buffer to it
                        return socketWrapper.write(contentBuffer);
                    });

        }
    }
}
