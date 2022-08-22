package io.github.sinri.rahab.v4.periscope;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.controlflow.FutureUntil;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.keel.servant.sisiodosi.KeelSisiodosi;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PeriscopeLens extends Periscope {
    private final NetClient netClient;
    private final String mirrorHost;
    private final int mirrorPort;
    private final String targetHost;
    private final int targetPort;

    private KeelLogger logger;

    private NetSocket socketToMirror;
    private final KeelSisiodosi sisiodosi;
    private final PhotonProcessor photonProcessor;
    private final Map<String, NetSocket> socketToTargetMap;

    public PeriscopeLens(String mirrorHost, int mirrorPort, String targetHost, int targetPort) {
        this.netClient = Keel.getVertx().createNetClient();

        this.mirrorPort = mirrorPort;
        this.mirrorHost = mirrorHost;
        this.targetHost = targetHost;
        this.targetPort = targetPort;

        this.logger = Keel.standaloneLogger("PeriscopeLens");
        this.sisiodosi = new KeelSisiodosi(getClass().getName());
        this.photonProcessor = new PhotonProcessor();
        this.socketToTargetMap = new ConcurrentHashMap<>();
    }

    public PeriscopeLens setLogger(KeelLogger logger) {
        this.logger = logger;
        return this;
    }

    public void start() {
        this.netClient.connect(mirrorPort, mirrorHost)
                .compose(socket -> {
                    String lockName = "LockForPeriscopeLensRecordBufferFromMirror";
                    this.socketToMirror = socket;
                    this.socketToMirror
                            .handler(bufferFromMirror -> {
                                Keel.getVertx().sharedData().getLock(lockName)
                                        .onSuccess(lock -> {
                                            this.sisiodosi.drop(v -> {
                                                return this.handleBufferFromMirror(bufferFromMirror);
                                            });
                                            lock.release();
                                        })
                                        .onFailure(throwable -> {
                                            this.logger.exception(lockName + " cannot be acquired", throwable);
                                        });
                            })
                            .endHandler(end -> {
                                this.logger.info("socketToMirror END");
                            })
                            .drainHandler(drain -> {
                                this.logger.info("socketToMirror DRAIN");
                                this.socketToMirror.resume();
                            })
                            .exceptionHandler(throwable -> {
                                this.logger.exception("socketToMirror ERROR", throwable);
                            })
                            .closeHandler(close -> {
                                this.logger.info("socketToMirror CLOSE");
                            });

                    return this.socketToMirror.write("[PeriscopeLensReport]");
                    //Photon registerPhoton = Photon.create(Buffer.buffer("LENS"), Buffer.buffer("REGISTER"));
                    //return this.socketToMirror.write(registerPhoton.toBuffer());
                }, throwable -> {
                    this.logger.exception("CONNECT FAILED", throwable);
                    return Keel.getVertx().close();
                });
    }

    /**
     * THREAD SAFE NEEDED!
     * RUN IN SISIODOSI.
     */
    private Future<Void> handleBufferFromMirror(Buffer bufferFromMirror) {
        this.photonProcessor.receive(bufferFromMirror);
        Queue<Photon> photonQueue = this.photonProcessor.getPhotonQueue();
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
                                    logger.exception(throwable);
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

        return getSocketToTargetMap(identity)
                .compose(socket -> {
                    // 2. send buffer to it
                    Future<Void> writeFuture = socket.write(contentBuffer);
                    if (socket.writeQueueFull()) {
                        socket.pause();
                    }
                    return writeFuture;
                });

    }

    private Future<NetSocket> getSocketToTargetMap(String identity) {
        NetSocket netSocket = this.socketToTargetMap.get(identity);
        if (netSocket == null) {
            return this.netClient.connect(this.targetPort, this.targetHost)
                    .compose(socketCreatedToTarget -> {
                        socketCreatedToTarget
                                .handler(buffer -> {
                                    Photon photon = Photon.create(Buffer.buffer(identity), buffer);
                                    this.socketToMirror.write(photon.toBuffer());
                                    if (this.socketToMirror.writeQueueFull()) {
                                        this.socketToMirror.pause();
                                    }
                                })
                                .endHandler(end -> {
                                    logger.debug("end");
                                })
                                .drainHandler(drain -> {
                                    logger.debug("drain");
                                    socketCreatedToTarget.resume();
                                })
                                .closeHandler(close -> {
                                    logger.info("close");
                                    this.socketToTargetMap.remove(identity);
                                });
                        this.socketToTargetMap.put(identity, socketCreatedToTarget);
                        return Future.succeededFuture(socketCreatedToTarget);
                    });
        } else {
            return Future.succeededFuture(netSocket);
        }
    }
}
