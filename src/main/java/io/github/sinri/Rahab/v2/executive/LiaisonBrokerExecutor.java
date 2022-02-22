package io.github.sinri.Rahab.v2.executive;

import io.github.sinri.Rahab.v2.wormhole.liaison.RahabLiaisonBroker;
import io.github.sinri.keel.Keel;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.TypedOption;

import java.util.List;

public class LiaisonBrokerExecutor extends RahabExecutor {
    public LiaisonBrokerExecutor(List<String> userCommandLineArguments) {
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

        new RahabLiaisonBroker()
                .listen(port)
                .onComplete(netServerAsyncResult -> {
                    if (netServerAsyncResult.failed()) {
                        getLogger().exception("RahabProxyBroker 启动失败", netServerAsyncResult.cause());
                        Keel.getVertx().close();
                    } else {
                        getLogger().notice("RahabProxyBroker 启动成功 端口 " + port);
                    }
                });
    }
}
