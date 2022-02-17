package io.github.sinri.Rahab.test.v2;

import io.github.sinri.keel.Keel;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;

public class ActualServerRunner {
    public static void main(String[] args) {
        Keel.loadPropertiesFromFile("config.properties");
        Keel.initializeVertx(new VertxOptions());

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setHost("rahab.leqee.com");
        httpServerOptions.setSsl(true);
        httpServerOptions.setKeyStoreOptions(new JksOptions()
                .setPath(Keel.getPropertiesReader().getProperty("ActualServer.ssl.jks.path"))
                .setPassword(Keel.getPropertiesReader().getProperty("ActualServer.ssl.jks.password"))
        );

        HttpServer originServer = Keel.getVertx().createHttpServer(httpServerOptions);

        originServer.requestHandler(req -> {
            req.response()
                    .putHeader("content-type", "text/html")
                    .end("<html>" +
                            "<body>" +
                            "<h1>I'm the Rahab!</h1>" +
                            "<p>port is 443</p>" +
                            "<p>" + req.host() + "</p>" +
                            "<p>" + req.path() + "</p>" +
                            "</body>" +
                            "</html>"
                    );
        }).listen(443);
    }
}
