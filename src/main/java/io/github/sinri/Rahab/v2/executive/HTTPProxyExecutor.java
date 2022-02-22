package io.github.sinri.Rahab.v2.executive;

import io.github.sinri.Rahab.v2.proxy.http.RahabHttpProxy;
import io.github.sinri.keel.Keel;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.TypedOption;

import java.util.List;

public class HTTPProxyExecutor extends RahabExecutor {

    public HTTPProxyExecutor(List<String> userCommandLineArguments) {
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
        new RahabHttpProxy().listen(port)
                .onSuccess(server -> {
                    getLogger().notice("RahabHttpProxy 开始运作 端口为 " + port);
                })
                .onFailure(throwable -> {
                    getLogger().exception("RahabHttpProxy 启动失败 端口为 " + port, throwable);
                    Keel.getVertx().close();
                });
    }
}
