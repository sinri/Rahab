package io.github.sinri.rahab.v4a.executive;

import io.github.sinri.rahab.v4a.consulate.ConsulateServer;
import io.github.sinri.rahab.v4a.proxy.socks5.RahabSocks5ProxyVerticle;
import io.github.sinri.rahab.v4a.proxy.socks5.auth.RahabSocks5AuthMethod;
import io.github.sinri.rahab.v4a.proxy.socks5.auth.impl.RahabSocks5AuthMethod00;
import io.vertx.core.Future;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.TypedOption;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConsulateServerExecutor extends RahabExecutor {
    public ConsulateServerExecutor(List<String> userCommandLineArguments) {
        super(userCommandLineArguments);
    }

    @Override
    protected CLI getCommandLineParseRule() {
        return getSharedCommandParseRule()
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setRequired(true)
                        .setLongName("port")
                        .setDescription("使用的端口")
                )
                .addOption(new TypedOption<String>()
                        .setType(String.class)
                        .setRequired(true)
                        .setLongName("server_ws_path")
                        .setDescription("HTTP WebSocket Service Path")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setRequired(true)
                        .setLongName("socks5_port")
                        .setDescription("SOCKS5使用的端口")
                )
                .addOption(new TypedOption<String>()
                        .setType(String.class)
                        .setRequired(false)
                        .setLongName("socks5_host")
                        .setDescription("SOCKS5使用的地址；如果不填，则内嵌")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setRequired(false)
                        .setLongName("idle_timeout_seconds")
                        .setDescription("空闲超时秒数")
                        .setDefaultValue("10")
                )
                .addOption(new TypedOption<String>()
                        .setType(String.class)
                        .setRequired(false)
                        .setLongName("jks_path")
                        .setDescription("SSL JKS PATH 不填的话就不使用SSL")
                )
                .addOption(new TypedOption<String>()
                        .setType(String.class)
                        .setRequired(false)
                        .setLongName("jks_password")
                        .setDescription("SSL JKS PASSWORD如果不填，则内嵌")
                        .setDefaultValue("")
                )
                ;
    }

    @Override
    protected void execute(CommandLine commandLine) {
        int port = commandLine.getOptionValue("port");
        int socks5_port = commandLine.getOptionValue("socks5_port");
        String socks5_host = commandLine.getOptionValue("socks5_host");
        String server_ws_path = commandLine.getOptionValue("server_ws_path");
        int idle_timeout_seconds = commandLine.getOptionValue("idle_timeout_seconds");
        String jksPath = commandLine.getOptionValue("jks_path");
        String jksPassword = commandLine.getOptionValue("jks_password");
        ensureSocks5(socks5_host, socks5_port, idle_timeout_seconds)
                .compose(s -> {
                    return new ConsulateServer(
                            server_ws_path,
                            port,
                            socks5_port,
                            (socks5_host == null ? "127.0.0.1" : socks5_host),
                            jksPath,
                            jksPassword
                    )
                            .deployMe();
                });


    }

    private Future<String> ensureSocks5(String socks5_host, int socks5_port, int idle_timeout_seconds) {
        if (socks5_host == null) {
            RahabSocks5AuthMethod00 rahabSocks5AuthMethod00 = new RahabSocks5AuthMethod00();

            Set<RahabSocks5AuthMethod> authMethodSet = new HashSet<>();
            authMethodSet.add(rahabSocks5AuthMethod00);

            RahabSocks5ProxyVerticle rahabSocks5ProxyVerticle = new RahabSocks5ProxyVerticle(socks5_port, authMethodSet, idle_timeout_seconds);
            return rahabSocks5ProxyVerticle.deployMe();
        } else {
            return Future.succeededFuture(null);
        }
    }
}
