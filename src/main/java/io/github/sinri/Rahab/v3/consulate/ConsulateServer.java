package io.github.sinri.Rahab.v3.consulate;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.verticles.KeelVerticle;
import io.github.sinri.keel.web.KeelHttpServer;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;

import java.util.Date;

public class ConsulateServer extends KeelVerticle {
    private final String websocketPath;
    private final int port;
    private final int socks5port;
    private final String socks5host;

    private final String jksPath;
    private final String jksPassword;

    public ConsulateServer(String websocketPath, int port, int socks5port, String socks5host, String jksPath, String jksPassword) {
        this.websocketPath = websocketPath;
        this.port = port;
        this.socks5host = socks5host;
        this.socks5port = socks5port;

        this.jksPath = jksPath;
        this.jksPassword = jksPassword;
    }

//    protected int getListenPort() {
//        return config().getInteger("port");
//    }
//
//    protected int getSocks5Port() {
//        return config().getInteger("socks5_port");
//    }
//
//    protected int getSocks5Host() {
//        return config().getInteger("socks5_host");
//    }

    @Override
    public void start() throws Exception {
        super.start();

        setLogger(Keel.standaloneLogger(getClass().getSimpleName()).setCategoryPrefix((new Date().getTime()) + "-" + deploymentID()));

        HttpServerOptions httpServerOptions = new HttpServerOptions()
                .setPort(port);

        if (this.jksPath != null) {
            httpServerOptions
                    .setSsl(true)
                    .setKeyStoreOptions(
                            new JksOptions()
                                    .setPath(jksPath)
                                    .setPassword(jksPassword)
                    );
        }

        KeelHttpServer keelHttpServer = new KeelHttpServer(Keel.getVertx(), httpServerOptions, true);
        keelHttpServer.setLogger(getLogger());

        keelHttpServer.setWebSocketHandlerToServer(
                ConsulateServerHandler.class,
                new DeploymentOptions()
                        .setConfig(new JsonObject()
                                .put("socks5_host", socks5host)
                                .put("socks5_port", socks5port)
                        )
        );

//        Route route = keelHttpServer.getRouter().route(websocketPath);
//        KeelWebSocketHandler.upgradeFromHttp(
//                route,
//                ConsulateServerHandler.class,
//                getLogger(),
//                new DeploymentOptions()
//                        .setConfig(new JsonObject()
//                                .put("socks5_host", socks5host)
//                                .put("socks5_port", socks5port)
//                        ));

        keelHttpServer.listen();
    }
}
