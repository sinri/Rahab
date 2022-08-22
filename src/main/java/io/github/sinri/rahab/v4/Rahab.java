package io.github.sinri.rahab.v4;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.properties.KeelPropertiesReader;
import io.github.sinri.rahab.v4.periscope.PeriscopeLens;
import io.github.sinri.rahab.v4.periscope.PeriscopeMirror;
import io.github.sinri.rahab.v4.proxy.socks5.RahabSocks5Proxy;
import io.github.sinri.rahab.v4.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.github.sinri.rahab.v4.proxy.socks5.auth.impl.RahabSocks5AuthMethod02;
import io.github.sinri.rahab.v4.wormhole.RahabWormhole;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Rahab {
    public static void main(String[] args) {
        Keel.loadPropertiesFromFile("config.properties");

        VertxOptions vertxOptions = new VertxOptions();

        // DNS SERVER
        String dnsProperty = Keel.getPropertiesReader().getProperty("dns.servers");
        AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
        if (dnsProperty != null && !dnsProperty.isEmpty()) {
            String[] split = dnsProperty.split("[,\\s]+");
            for (var x : split) {
                addressResolverOptions.addServer(x);
            }
        } else {
            addressResolverOptions
                    .addServer("119.29.29.29")
                    .addServer("223.5.5.5")
                    .addServer("1.1.1.1")
                    .addServer("114.114.114.114")
                    .addServer("8.8.8.8");
        }
        vertxOptions.setAddressResolverOptions(addressResolverOptions);

        Keel.initializeVertx(vertxOptions);

        String programName = Keel.getPropertiesReader().getProperty("rahab.program");
        KeelPropertiesReader programProperties = Keel.getPropertiesReader().filter("rahab." + programName);
        // wormhole,proxy-socks5,periscope-mirror,periscope-lens
        switch (programName) {
            case "wormhole":
                new RahabWormhole(
                        Integer.parseInt(programProperties.getProperty("port")),
                        programProperties.getProperty("target.host"),
                        Integer.parseInt(programProperties.getProperty("target.port"))
                )
                        .run();
                break;
            case "proxy-socks5":
                String authMethod = programProperties.getProperty("auth-method");
                if ("00".equals(authMethod)) {
                    new RahabSocks5Proxy(Integer.parseInt(programProperties.getProperty("port")))
                            .run();
                } else if ("02".equals(authMethod)) {
                    String usernameInConfig = programProperties.getProperty("auth-method-02.username");
                    String passwordInConfig = programProperties.getProperty("auth-method-02.password");
                    Map<Byte, RahabSocks5AuthMethod> map = new HashMap<>();
                    map.put((byte) 2, new RahabSocks5AuthMethod02(new RahabSocks5AuthMethod02.UsernamePasswordVerifier() {
                        @Override
                        public Future<Boolean> verify(String username, String password) {
                            return Future.succeededFuture(
                                    Objects.equals(usernameInConfig, username)
                                            && Objects.equals(passwordInConfig, password)
                            );
                        }
                    }));
                    new RahabSocks5Proxy(
                            Integer.parseInt(programProperties.getProperty("port")),
                            map
                    )
                            .run();
                }
                break;
            case "periscope-mirror":
                new PeriscopeMirror(Integer.parseInt(programProperties.getProperty("port")))
                        .run();
                break;
            case "periscope-lens":
                new PeriscopeLens(
                        programProperties.getProperty("mirror.host"),
                        Integer.parseInt(programProperties.getProperty("mirror.port")),
                        programProperties.getProperty("target.host"),
                        Integer.parseInt(programProperties.getProperty("target.port"))
                )
                        .run();
                break;
            default:
                System.out.println("ERROR");
        }
    }
}
