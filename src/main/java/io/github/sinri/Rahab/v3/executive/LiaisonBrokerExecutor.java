package io.github.sinri.Rahab.v3.executive;

import io.github.sinri.Rahab.v3.wormhole.liaison.RahabLiaisonBrokerVerticle;
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

        new RahabLiaisonBrokerVerticle(port).deployMe();
    }
}
