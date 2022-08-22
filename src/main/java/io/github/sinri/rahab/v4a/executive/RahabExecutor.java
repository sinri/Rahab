package io.github.sinri.rahab.v4a.executive;

import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.github.sinri.rahab.v4a.Rahab;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;

import java.util.List;

abstract public class RahabExecutor {
    final static public String MODE_HTTP_PROXY = "HttpProxy";
    final static public String MODE_WORMHOLE = "Wormhole";
    final static public String MODE_LIAISON_BROKER = "LiaisonBroker";
    final static public String MODE_LIAISON_SOURCE = "LiaisonSource";
    final static public String MODE_SOCKS5_PROXY = "Socks5Proxy";

    final static public String MODE_CONSULATE_CLIENT = "ConsulateClient";
    final static public String MODE_CONSULATE_SERVER = "ConsulateServer";

    private final List<String> userCommandLineArguments;

    protected KeelLogger getLogger() {
        return Keel.outputLogger(getClass().getSimpleName());
    }

    public RahabExecutor(List<String> userCommandLineArguments) {
        this.userCommandLineArguments = userCommandLineArguments;
    }

    protected final CLI getSharedCommandParseRule() {
        return CLI.create("Rahab-" + Rahab.VERSION + ".jar")
                .setSummary("Rahab " + Rahab.VERSION + " [" + getClass().getSimpleName() + "] 启动命令")
                .addOption(new Option()
                        .setLongName("help")
                        .setShortName("h")
                        .setDescription("获取命令帮助")
                        .setFlag(true)
                        .setHelp(true)
                )
                .addOption(new Option()
                        .setLongName("mode")
                        .setRequired(true)
                        .setDescription(
                                "运行模式 支持的选项为 " + Keel.stringHelper().joinStringArray(
                                        List.of(RahabExecutor.MODE_HTTP_PROXY,
                                                RahabExecutor.MODE_WORMHOLE,
                                                RahabExecutor.MODE_SOCKS5_PROXY,
                                                RahabExecutor.MODE_LIAISON_SOURCE,
                                                RahabExecutor.MODE_LIAISON_BROKER,
                                                RahabExecutor.MODE_CONSULATE_CLIENT,
                                                RahabExecutor.MODE_CONSULATE_SERVER
                                        ),
                                        ","
                                )
                        )
                );
    }

    abstract protected CLI getCommandLineParseRule();

    abstract protected void execute(CommandLine commandLine);

    protected void showHelp() {
        StringBuilder builder = new StringBuilder();
        getCommandLineParseRule().usage(builder, "java -jar");
        System.out.println(builder);
    }

    final public void run() {
        CommandLine commandLine = getCommandLineParseRule().parse(userCommandLineArguments, false);
        if (commandLine.isValid() && !commandLine.isAskingForHelp()) {
            try {
                execute(commandLine);
            } catch (Throwable throwable) {
                getLogger().exception("Running Throwable Caught", throwable);
            }
            return;
        }

        showHelp();
        if (commandLine.isAskingForHelp()) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }
}
