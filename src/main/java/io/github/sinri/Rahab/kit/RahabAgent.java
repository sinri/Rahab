package io.github.sinri.Rahab.kit;

import io.github.sinri.Rahab.logger.RahabLogger;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;

public class RahabAgent {
    public final static String MODE_LOCAL_AGENT="LOCAL";
    public final static String MODE_REMOTE_AGENT="REMOTE";
    private static Vertx vertx;
    private static int port;
    private static String mode;
    private static String remoteAddress;
    private static int remotePort;

    public static int getPort() {
        return port;
    }

    public static void setPort(int port) {
        RahabAgent.port = port;
    }

    public static String getMode() {
        return mode;
    }

    public static void setMode(String mode) {
        RahabAgent.mode = mode;
    }

    public static String getRemoteAddress() {
        return remoteAddress;
    }

    public static void setRemoteAddress(String remoteAddress) {
        RahabAgent.remoteAddress = remoteAddress;
    }

    /**
     * 此方法应该先于VertxHttpGateway实例构造器执行，
     * 以初始化配置管理器。
     *
     * @param poolSize the size of pool
     */
    public static void initializeVertx(int poolSize) {
        vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(poolSize));

        RahabLogger.getLogger(RahabAgent.class).info("initializeVertx done");
    }

    public static Vertx getVertx() {
        return vertx;
    }

    public static int getRemotePort() {
        return remotePort;
    }

    public static void setRemotePort(int remotePort) {
        RahabAgent.remotePort = remotePort;
    }

    /**
     * 网关的标准运行入口，以配置选项执行网关
     */
    public void run() {
        // 建立网关服务器
        HttpServerOptions options = new HttpServerOptions().setLogActivity(true);
        HttpServer gatewayServer = getVertx().createHttpServer(options);
        // 如果网关服务器出现异常，则进行处理
        gatewayServer.exceptionHandler(exception -> {
            RahabLogger.getLogger(this.getClass()).info("网关HTTP服务出现异常", exception);
        });

        // 网关服务器处理请求
        gatewayServer.requestHandler(request -> {
            // 创建网关请求封装类，根据路由设置或者判断filters数量，检查是否需要filters
            try {
                //new GatewayRequest(request, router).filterAndProxy();
                if(mode.equals(MODE_LOCAL_AGENT)){
                    // local work: cache the request and send to remote
                    (new HandlerForLocal(request)).handle();
                } else if (mode.equals(MODE_REMOTE_AGENT)) {
                    // remote work: rebuild the cached request and execute
                    (new HandlerForRemote(request)).handle();
                } else {
                    throw new Exception("The mode " + mode + " is not supported");
                }
            } catch (Exception e) {
                //e.printStackTrace();
                RahabLogger.getLogger(this.getClass()).error("大势已去。" + e.getMessage());
            }
        }).listen(port, server -> {
            if (server.succeeded()) {
                RahabLogger.getLogger(this.getClass()).info("HTTP服务已经站立在服务器上。端口:" + port + "。");
            }
            if (server.failed()) {
                RahabLogger.getLogger(this.getClass()).error("HTTP服务无法站立在服务器上。端口:" + port + "。" + server.cause().getMessage());
                throw new RuntimeException(server.cause());
            }
        });


    }
}
