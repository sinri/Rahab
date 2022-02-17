package io.github.sinri.Rahab;

import io.github.sinri.Rahab.v2.RahabHttpProxy;
import io.github.sinri.Rahab.v2.Wormhole;
import io.github.sinri.Rahab.v2.transform.impl.http.client.TransformerFromHttpRequestToRaw;
import io.github.sinri.Rahab.v2.transform.impl.http.client.TransformerFromRawToHttpRequest;
import io.github.sinri.Rahab.v2.transform.impl.http.server.TransformerFromHttpResponseToRaw;
import io.github.sinri.Rahab.v2.transform.impl.http.server.TransformerFromRawToHttpResponse;
import io.github.sinri.keel.Keel;
import io.github.sinri.keel.core.logger.KeelLogger;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.TypedOption;
import io.vertx.core.dns.AddressResolverOptions;

import java.util.*;

public class Rahab {
    public static void main(String[] args) {
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

        KeelLogger mainLogger = Keel.outputLogger("RahabMain");

        CLI mainCLI = CLI.create("RahabV2")
                .setSummary("Rahab 2 启动命令")
                .addOption(new Option()
                        .setLongName("help")
                        .setShortName("h")
                        .setDescription("获取命令帮助")
                        .setFlag(true)
                        .setHelp(true)
                )
                .addOption(new Option()
                        .setLongName("mode")
                        .setChoices(Set.of("HttpProxy", "Wormhole"))
                        .setRequired(true)
                        .setDescription("运行模式，可选 HttpProxy 或 Wormhole")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setRequired(true)
                        .setLongName("port")
                        .setDescription("使用的端口")
                )
                .addOption(new Option()
                        .setLongName("WormholeName")
                        .setDefaultValue(UUID.randomUUID().toString())
                        .setDescription("虫洞的名称")
                )
                .addOption(new Option()
                        .setLongName("WormholeTransformer")
                        .setChoices(Set.of("HttpServer", "HttpClient"))
                        .setDefaultValue("")
                        .setDescription("作为虫洞运行时使用的数据混淆器")
                )
                .addOption(new Option()
                        .setLongName("WormholeTransformerHttpFakeHost")
                        .setDefaultValue("cloud.player.com")
                        .setDescription("作为虫洞运行时,使用数据混淆器【HttpClient】时指定一个伪装的域名")
                )
                .addOption(new Option()
                        .setLongName("WormholeDestinationHost")
                        .setDefaultValue("127.0.0.1")
                        .setDescription("作为虫洞运行时使用的远程地址")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setLongName("WormholeDestinationPort")
                        .setDefaultValue(String.valueOf(7999))
                        .setDescription("作为虫洞运行时使用的远程端口")
                );


        List<String> userCommandLineArguments = new ArrayList<>(Arrays.asList(args));
        CommandLine commandLine = mainCLI.parse(userCommandLineArguments, false);

        if (commandLine.isValid()) {
            String mode = commandLine.getOptionValue("mode");
            Integer port = commandLine.getOptionValue("port");

            if (mode.equals("HttpProxy")) {
                // as HttpProxy
                runAsHttpProxy(port);
                return;
            } else if (mode.equals("Wormhole")) {
                // as Wormhole
                String destinationHost = commandLine.getOptionValue("WormholeDestinationHost");
                Integer destinationPort = commandLine.getOptionValue("WormholeDestinationPort");
                String wormholeTransformerCode = commandLine.getOptionValue("WormholeTransformer");
                String wormholeName = commandLine.getOptionValue("WormholeName");
                String fakeHost = commandLine.getOptionValue("WormholeTransformerHttpFakeHost");

                runAsWormhole(wormholeName, port, destinationHost, destinationPort, wormholeTransformerCode, fakeHost);
                return;
            }
        }

        StringBuilder builder = new StringBuilder();
        mainCLI.usage(builder);
        mainLogger.print(builder.toString());
        System.exit(1);
    }

    private static void runAsHttpProxy(int port) {
        KeelLogger mainLogger = Keel.outputLogger("RahabMain");

        new RahabHttpProxy().listen(port)
                .onSuccess(server -> {
                    mainLogger.notice("RahabHttpProxy 开始运作 端口为 " + port);
                })
                .onFailure(throwable -> {
                    mainLogger.exception("RahabHttpProxy 启动失败 端口为 " + port, throwable);
                    Keel.getVertx().close();
                });
    }

    private static void runAsWormhole(String wormholeName, int port, String destinationHost, Integer destinationPort, String wormholeTransformerCode, String fakeHost) {
        KeelLogger mainLogger = Keel.outputLogger("RahabMain");

        Wormhole wormhole = new Wormhole(wormholeName, destinationHost, destinationPort);
        if (wormholeTransformerCode.equals("HttpServer")) {
            wormhole
                    .setTransformerForDataFromRemote(new TransformerFromRawToHttpResponse())
                    .setTransformerForDataFromLocal(new TransformerFromHttpRequestToRaw());
        } else if (wormholeTransformerCode.equals("HttpClient")) {
            wormhole
                    .setTransformerForDataFromRemote(new TransformerFromHttpResponseToRaw())
                    .setTransformerForDataFromLocal(new TransformerFromRawToHttpRequest(fakeHost));
        }

        wormhole.listen(port)
                .onSuccess(server -> {
                    mainLogger.notice("WormholeProxy [" + wormholeName + "] 端口 " + port + " 开始运作，终点 " + destinationHost + ":" + destinationPort);
                })
                .onFailure(throwable -> {
                    mainLogger.exception("WormholeProxy [" + wormholeName + "] 端口 " + port + " 启动失败", throwable);
                });
    }
}
