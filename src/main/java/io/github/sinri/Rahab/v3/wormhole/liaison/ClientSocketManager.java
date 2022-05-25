package io.github.sinri.Rahab.v3.wormhole.liaison;

import io.vertx.core.net.NetSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSocketManager {

    private final Map<String, NetSocket> clientSocketMap;

    public ClientSocketManager() {
        clientSocketMap = new ConcurrentHashMap<>();
    }

    /**
     * @return 客户端通讯映射
     */
    public Map<String, NetSocket> getClientSocketMap() {
        return clientSocketMap;
        //return Keel.getVertx().sharedData().getLocalMap("RahabLiaisonBrokerVerticle::clientSocketMap");
    }

    /**
     * 代理卧底通讯 注册
     *
     * @param proxySocket 代理卧底通讯
     */
    public void registerProxySocket(NetSocket proxySocket) {
        getClientSocketMap().put("", proxySocket);
    }

    /**
     * 代理卧底通讯 撤销
     */
    public void unregisterProxySocket() {
        getClientSocketMap().remove("");
    }

    /**
     * @return 代理卧底通讯 or null
     */
    public NetSocket getProxySocket() {
        return getClientSocketMap().get("");
    }
}
