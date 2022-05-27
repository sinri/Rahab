package io.github.sinri.Rahab;

import io.github.sinri.Rahab.v3.executive.*;
import io.github.sinri.keel.Keel;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.dns.AddressResolverOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Rahab {
    public final static String VERSION = "3.0.2";

    private static void initializeVertx() {
        Keel.loadPropertiesFromFile("config.properties");

        VertxOptions vertxOptions = new VertxOptions();

        // DNS SERVER
        String dnsProperty = Keel.getPropertiesReader().getProperty("dns.servers");
        AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
        if (dnsProperty != null && !dnsProperty.isEmpty()) {
            String[] split = dnsProperty.split("[,\\s]+");
            for (var x : split) {
                addressResolverOptions.addServer(x);
            }
        } else {
            addressResolverOptions
                    .addServer("119.29.29.29")
                    .addServer("223.5.5.5")
                    .addServer("1.1.1.1")
                    .addServer("114.114.114.114")
                    .addServer("8.8.8.8");
        }
        vertxOptions.setAddressResolverOptions(addressResolverOptions);

        Keel.initializeVertx(vertxOptions);
    }

    public static void main(String[] args) {
        initializeVertx();

        List<String> userCommandLineArguments = new ArrayList<>(Arrays.asList(args));

        //commandParserV21(userCommandLineArguments);

        CLI mainCLI = CLI.create("Rahab-" + VERSION + ".jar")
                .setSummary("Rahab " + VERSION + " 启动命令")
                .addOption(new Option()
                        .setLongName("help")
                        .setShortName("h")
                        .setDescription("获取命令帮助")
                        .setFlag(true)
                        .setHelp(true)
                )
                .addOption(new Option()
                        .setLongName("mode")
                        .setChoices(Set.of(
                                RahabExecutor.MODE_HTTP_PROXY,
                                RahabExecutor.MODE_WORMHOLE,
                                RahabExecutor.MODE_SOCKS5_PROXY,
                                RahabExecutor.MODE_LIAISON_SOURCE,
                                RahabExecutor.MODE_LIAISON_BROKER,
                                RahabExecutor.MODE_CONSULATE_CLIENT,
                                RahabExecutor.MODE_CONSULATE_SERVER
                        ))
                        .setRequired(true)
                        .setDescription("运行模式")
                );
        CommandLine commandLine = mainCLI.parse(userCommandLineArguments, false);

        if (commandLine.isValid()) {
            String mode = commandLine.getOptionValue("mode");

            RahabExecutor executor;
            switch (mode) {
                case RahabExecutor.MODE_HTTP_PROXY:
                    executor = new HTTPProxyExecutor(userCommandLineArguments);
                    break;
                case RahabExecutor.MODE_SOCKS5_PROXY:
                    executor = new Socks5ProxyExecutor(userCommandLineArguments);
                    break;
                case RahabExecutor.MODE_WORMHOLE:
                    executor = new WormholeExecutor(userCommandLineArguments);
                    break;
                case RahabExecutor.MODE_LIAISON_BROKER:
                    executor = new LiaisonBrokerExecutor(userCommandLineArguments);
                    break;
                case RahabExecutor.MODE_LIAISON_SOURCE:
                    executor = new LiaisonSourceExecutor(userCommandLineArguments);
                    break;
                case RahabExecutor.MODE_CONSULATE_CLIENT:
                    executor = new ConsulateClientExecutor(userCommandLineArguments);
                    break;
                case RahabExecutor.MODE_CONSULATE_SERVER:
                    executor = new ConsulateServerExecutor(userCommandLineArguments);
                    break;
                default:
                    executor = new RahabExecutor(userCommandLineArguments) {
                        @Override
                        protected CLI getCommandLineParseRule() {
                            return getSharedCommandParseRule();
                        }

                        @Override
                        protected void execute(CommandLine commandLine) {
                            StringBuilder builder = new StringBuilder();
                            getCommandLineParseRule().usage(builder, "java -jar");
                            System.out.println(builder);
                            System.exit(1);
                        }
                    };
            }

            executor.run();
            return;
        }

        StringBuilder builder = new StringBuilder();
        mainCLI.usage(builder, "java -jar");
        System.out.println(builder);
        System.exit(1);
    }
}
