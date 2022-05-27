package io.github.sinri.Rahab.v3.executive;

import io.github.sinri.Rahab.v3.proxy.socks5.RahabSocks5ProxyVerticle;
import io.github.sinri.Rahab.v3.proxy.socks5.auth.impl.RahabSocks5AuthMethod00;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.TypedOption;

import java.util.List;
import java.util.Set;

public class Socks5ProxyExecutor extends RahabExecutor {
    public Socks5ProxyExecutor(List<String> userCommandLineArguments) {
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
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setRequired(false)
                        .setLongName("idle_timeout_seconds")
                        .setDescription("空闲超时秒数")
                        .setDefaultValue("10")
                );
    }

    @Override
    protected void execute(CommandLine commandLine) {
        int port = commandLine.getOptionValue("port");
        int idle_timeout_seconds = commandLine.getOptionValue("idle_timeout_seconds");

        new RahabSocks5ProxyVerticle(port, Set.of(new RahabSocks5AuthMethod00()), idle_timeout_seconds)
                .deployMe();
    }
}
