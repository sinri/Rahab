package io.github.sinri.rahab.v4.wormhole.liaison;

import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.Future;
import io.vertx.core.net.NetSocket;

import java.util.HashMap;
import java.util.Map;

abstract public class SourceWorkerGenerator {
    private final Map<String, RahabLiaisonSourceWorker> clientWorkerMap = new HashMap<>();

    /**
     * Just create, and set the special properties, not set the shared properties nor run initialize here.
     *
     * @return RahabLiaisonSourceWorker
     */
    abstract protected RahabLiaisonSourceWorker generateWorker();

    public final Future<RahabLiaisonSourceWorker> getWorkerForClient(String clientID, NetSocket clientSocket, KeelLogger logger) {
        RahabLiaisonSourceWorker rahabLiaisonSourceWorker = clientWorkerMap.get(clientID);
        if (rahabLiaisonSourceWorker == null) {
            rahabLiaisonSourceWorker = generateWorker();
            if (rahabLiaisonSourceWorker == null) {
                return Future.failedFuture("无法创建 RahabLiaisonSourceWorker");
            }
            rahabLiaisonSourceWorker.setLogger(logger);
            rahabLiaisonSourceWorker.setClientID(clientID);
            rahabLiaisonSourceWorker.setClientSocket(clientSocket);
            RahabLiaisonSourceWorker finalRahabLiaisonSourceWorker = rahabLiaisonSourceWorker;
            rahabLiaisonSourceWorker.initialize()
                    .compose(v -> {
                        clientWorkerMap.put(clientID, finalRahabLiaisonSourceWorker);
                        return Future.succeededFuture(finalRahabLiaisonSourceWorker);
                    });

        }
        return Future.succeededFuture(rahabLiaisonSourceWorker);
    }

    public final SourceWorkerGenerator removeWorkerForClient(String clientID) {
        clientWorkerMap.remove(clientID);
        return this;
    }
}
