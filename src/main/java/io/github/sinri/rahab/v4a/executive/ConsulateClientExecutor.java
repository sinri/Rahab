package io.github.sinri.rahab.v4a.executive;

import io.github.sinri.rahab.v4a.consulate.ConsulateClient;
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
//                .addOption(new TypedOption<String>()
//                        .setType(String.class)
//                        .setRequired(true)
//                        .setLongName("server_ws_uri")
//                        .setDescription("HTTP WebSocket Service URI")
//                )
                .addOption(new TypedOption<String>()
                        .setType(String.class)
                        .setRequired(true)
                        .setLongName("ws_host")
                        .setDescription("HTTP WebSocket Service Host")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setRequired(true)
                        .setLongName("ws_port")
                        .setDescription("HTTP WebSocket Service Post")
                )
                .addOption(new TypedOption<String>()
                        .setType(String.class)
                        .setRequired(true)
                        .setLongName("ws_path")
                        .setDescription("HTTP WebSocket Service Path")
                )
                ;
    }

    @Override
    protected void execute(CommandLine commandLine) {
        int port = commandLine.getOptionValue("port");
//        String server_ws_uri = commandLine.getOptionValue("server_ws_uri");
        String wsHost = commandLine.getOptionValue("ws_host");
        int wsPort = commandLine.getOptionValue("ws_port");
        String wsPath = commandLine.getOptionValue("ws_path");
        new ConsulateClient(port, wsHost, wsPort, wsPath)
                .deployMe();
    }
}
