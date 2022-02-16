package io.github.sinri.Rahab.test.v2;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.httpproxy.HttpProxy;

public class ProxyRunner {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        // Create the proxy server that listens to port 8080 with HttpProxy instance that handles reverse proxy logic accordingly.

        HttpClient proxyClient = vertx.createHttpClient();

        HttpProxy proxy = HttpProxy.reverseProxy(proxyClient);
        proxy.origin(443, "rahab.leqee.com");

        HttpServer proxyServer = vertx.createHttpServer();
        proxyServer.requestHandler(proxy).listen(8080);
    }
}
