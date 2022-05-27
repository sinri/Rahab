package io.github.sinri.Rahab.v3.executive;

import io.github.sinri.Rahab.v3.consulate.ConsulateClient;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.TypedOption;

import java.util.List;

public class ConsulateClientExecutor extends RahabExecutor {
    public ConsulateClientExecutor(List<String> userCommandLineArguments) {
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
                        .setLongName("server_ws_uri")
                        .setDescription("HTTP WebSocket Service URI")
                );
    }

    @Override
    protected void execute(CommandLine commandLine) {
        int port = commandLine.getOptionValue("port");
        String server_ws_uri = commandLine.getOptionValue("server_ws_uri");
        new ConsulateClient(port, server_ws_uri)
                .deployMe();
    }
}
