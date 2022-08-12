package io.github.sinri.rahab.v4.wormhole.liaison;

import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.lang.reflect.InvocationTargetException;

public interface RahabLiaisonSourceWorker {

    RahabLiaisonSourceWorker setClientSocket(NetSocket clientSocket);

    RahabLiaisonSourceWorker setClientID(String clientID);

    RahabLiaisonSourceWorker setLogger(KeelLogger logger);

    Future<Void> initialize();

    void handle(Buffer rawBufferFromClient);

    Future<Void> close();

    static <T extends RahabLiaisonSourceWorker> T buildNewWorker(String clientID, Class<T> className) {
        try {
            T t = className.getConstructor().newInstance();
            t.setClientID(clientID);
            return t;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }
}
