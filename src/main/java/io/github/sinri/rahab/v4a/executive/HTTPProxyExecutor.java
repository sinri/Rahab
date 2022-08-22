package io.github.sinri.rahab.v4a.executive;

import io.github.sinri.rahab.v4a.proxy.http.RahabHttpProxyVerticle;
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

        new RahabHttpProxyVerticle(port).deployMe();
    }
}
