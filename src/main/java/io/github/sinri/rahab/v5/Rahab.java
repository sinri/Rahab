package io.github.sinri.rahab.v5;

import io.vertx.core.*;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.net.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Configuration-compatible entry point for the four v4 operating modes. */
public final class Rahab {
    private Rahab() {}
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && (args[0].equals("-h") || args[0].equals("--help"))) {
            System.out.println("Rahab 5: java -jar Rahab-5.0-SNAPSHOT.jar [config.properties]");
            return;
        }
        if (args.length > 1) throw new IllegalArgumentException("Expected at most one configuration path");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(args.length == 0 ? "config.properties" : args[0]))) {
            properties.load(input);
        }
        Config config = new Config(properties);
        config.validate();
        VertxOptions options = new VertxOptions();
        String dns = properties.getProperty("dns.servers", "").trim();
        if (!dns.isEmpty()) {
            AddressResolverOptions resolver = new AddressResolverOptions();
            for (String server : dns.split("[,\\s]+")) resolver.addServer(server);
            options.setAddressResolverOptions(resolver);
        }
        Vertx vertx = Vertx.vertx(options);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS); }
            catch (Exception e) { System.err.println("Shutdown: " + e.getMessage()); }
        }, "rahab-shutdown"));
        try {
            start(vertx, config).toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
            System.out.println("Rahab 5 started: " + config.mode);
        } catch (Exception e) {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            throw e;
        }
    }

    static Future<Void> start(Vertx vertx, Config config) {
        config.validate();
        return vertx.deployVerticle(new AbstractVerticle() {
            @Override public void start(Promise<Void> promise) {
                startOnContext(vertx, config).onComplete(promise);
            }
        }).mapEmpty();
    }

    private static Future<Void> startOnContext(Vertx vertx, Config config) {
        NetClient client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(10_000));
        String host = config.get("host", "0.0.0.0");
        return switch (config.mode) {
            case "wormhole" -> vertx.createNetServer().connectHandler(socket ->
                    TcpRelay.connect(socket, client, config.required("target.host"), config.port("target.port")))
                    .listen(config.port("port"), host).mapEmpty();
            case "proxy-socks5" -> {
                String user = config.get("auth-method", "00").equals("02") ? config.required("auth-method-02.username") : null;
                String pass = user == null ? null : config.required("auth-method-02.password");
                yield vertx.createNetServer().connectHandler(socket -> new Socks5Session(vertx, socket, client, user, pass))
                        .listen(config.port("port"), host).mapEmpty();
            }
            case "periscope-mirror" -> Periscope.mirror(vertx, host, config.port("port"), config.port("lens.port"), config.required("token")).mapEmpty();
            case "periscope-lens" -> Periscope.lens(vertx, client, config.required("mirror.host"), config.port("mirror.port"),
                    config.required("target.host"), config.port("target.port"), config.required("token"));
            default -> Future.failedFuture("Unknown program: " + config.mode);
        };
    }

    static final class Config {
        final Properties properties;
        final String mode;
        Config(Properties properties) {
            this.properties = properties;
            mode = properties.getProperty("rahab.program", "").trim();
        }
        String get(String name, String fallback) { return properties.getProperty("rahab." + mode + "." + name, fallback); }
        String required(String name) {
            String value = get(name, "");
            if (value.isBlank()) throw new IllegalArgumentException("Missing rahab." + mode + "." + name);
            return value;
        }
        int port(String name) {
            int value = Integer.parseInt(required(name));
            if (value < 1 || value > 65535) throw new IllegalArgumentException("Invalid port: " + name);
            return value;
        }
        private void token() {
            if (required("token").getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096)
                throw new IllegalArgumentException("Periscope token exceeds 4096 bytes");
        }
        void validate() {
            switch (mode) {
                case "wormhole" -> { port("port"); required("target.host"); port("target.port"); }
                case "proxy-socks5" -> {
                    port("port");
                    String method = get("auth-method", "00");
                    if (!method.equals("00") && !method.equals("02")) throw new IllegalArgumentException("Unsupported auth-method");
                    if (method.equals("02")) {
                        if (required("auth-method-02.username").getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255
                                || required("auth-method-02.password").getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255)
                            throw new IllegalArgumentException("SOCKS5 credentials exceed 255 bytes");
                    }
                }
                case "periscope-mirror" -> {
                    port("port"); port("lens.port"); token();
                    if (port("port") == port("lens.port")) throw new IllegalArgumentException("Viewer and lens ports must differ");
                }
                case "periscope-lens" -> {
                    required("mirror.host"); port("mirror.port"); required("target.host"); port("target.port"); token();
                }
                default -> throw new IllegalArgumentException("Unknown rahab.program: " + mode);
            }
        }
    }
}
