package io.github.sinri.Rahab.v2.executive;

import io.github.sinri.Rahab.v2.proxy.socks5.RahabSocks5Proxy;
import io.github.sinri.Rahab.v2.proxy.socks5.auth.impl.RahabSocks5AuthMethod00;
import io.github.sinri.keel.Keel;
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
        new RahabSocks5Proxy(Set.of(new RahabSocks5AuthMethod00()))
                .listen(port)
                .onSuccess(server -> {
                    getLogger().notice("RahabSocks5Proxy 开始运作 端口为 " + port);
                })
                .onFailure(throwable -> {
                    getLogger().exception("RahabSocks5Proxy 启动失败 端口为 " + port, throwable);
                    Keel.getVertx().close();
                });
    }
}
