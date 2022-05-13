package io.github.sinri.Rahab.v2.executive;

import io.github.sinri.Rahab.v2.proxy.socks5.RahabSocks5ProxyVerticle;
import io.github.sinri.Rahab.v2.proxy.socks5.auth.impl.RahabSocks5AuthMethod00;
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
                );
    }

    @Override
    protected void execute(CommandLine commandLine) {
        int port = commandLine.getOptionValue("port");

        new RahabSocks5ProxyVerticle(port, Set.of(new RahabSocks5AuthMethod00())).deployMe();
    }
}
