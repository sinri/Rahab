package io.github.sinri.Rahab;

import io.github.sinri.Rahab.v2.executive.*;
import io.github.sinri.Rahab.v2.proxy.http.RahabHttpProxy;
import io.github.sinri.Rahab.v2.wormhole.Wormhole;
import io.github.sinri.Rahab.v2.wormhole.liaison.RahabLiaisonBroker;
import io.github.sinri.Rahab.v2.wormhole.liaison.RahabLiaisonSource;
import io.github.sinri.Rahab.v2.wormhole.liaison.RahabLiaisonSourceWorker;
import io.github.sinri.Rahab.v2.wormhole.liaison.SourceWorkerGenerator;
import io.github.sinri.Rahab.v2.wormhole.liaison.impl.RahabLiaisonSourceWorkerAsWormhole;
import io.github.sinri.Rahab.v2.wormhole.transform.impl.http.client.TransformerFromHttpRequestToRaw;
import io.github.sinri.Rahab.v2.wormhole.transform.impl.http.client.TransformerFromRawToHttpRequest;
import io.github.sinri.Rahab.v2.wormhole.transform.impl.http.server.TransformerFromHttpResponseToRaw;
import io.github.sinri.Rahab.v2.wormhole.transform.impl.http.server.TransformerFromRawToHttpResponse;
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
    public final static String VERSION = "2.2.0";

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
                                RahabExecutor.MODE_LIAISON_BROKER
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

    private static void commandParserV21(List<String> userCommandLineArguments) {
        KeelLogger mainLogger = Keel.outputLogger("RahabMain");

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
                        .setChoices(Set.of("HttpProxy", "Wormhole", "LiaisonBroker", "LiaisonSource"))
                        .setRequired(true)
                        .setDescription("运行模式")
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
                )
                .addOption(new Option()
                        .setLongName("LiaisonSourceWorker")
                        .setChoices(Set.of("wormhole"))
                        .setDefaultValue("wormhole")
                        .setDescription("情报源的工作方式")
                )
                .addOption(new Option()
                        .setLongName("LiaisonSourceWorkerWormholeHost")
                        .setDefaultValue("127.0.0.1")
                        .setDescription("情报源使用的掮客服务的远程地址")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setLongName("LiaisonSourceWorkerWormholePort")
                        .setDefaultValue(String.valueOf(7999))
                        .setDescription("情报源使用的掮客服务的远程端口")
                )
                .addOption(new Option()
                        .setLongName("LiaisonBrokerHost")
                        .setDefaultValue("127.0.0.1")
                        .setDescription("情报源使用的掮客服务的远程地址")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setLongName("LiaisonBrokerPort")
                        .setDefaultValue(String.valueOf(7999))
                        .setDescription("情报源使用的掮客服务的远程端口")
                );


        CommandLine commandLine = mainCLI.parse(userCommandLineArguments, false);

        if (commandLine.isValid() && !commandLine.isAskingForHelp()) {
            String mode = commandLine.getOptionValue("mode");
            Integer port = commandLine.getOptionValue("port");

            switch (mode) {
                case "HttpProxy":
                    // as HttpProxy
                    runAsHttpProxy(port);
                    return;
                case "Wormhole":
                    // as Wormhole
                    String destinationHost = commandLine.getOptionValue("WormholeDestinationHost");
                    Integer destinationPort = commandLine.getOptionValue("WormholeDestinationPort");
                    String wormholeTransformerCode = commandLine.getOptionValue("WormholeTransformer");
                    String wormholeName = commandLine.getOptionValue("WormholeName");
                    String fakeHost = commandLine.getOptionValue("WormholeTransformerHttpFakeHost");

                    runAsWormhole(wormholeName, port, destinationHost, destinationPort, wormholeTransformerCode, fakeHost);
                    return;
                case "LiaisonBroker":
                    // as RahabLiaisonBroker
                    runAsLiaisonBroker(port);
                    return;
                case "LiaisonSource":
                    // as RahabLiaisonSource
                    String sourceWorker = commandLine.getOptionValue("LiaisonSourceWorker");
                    SourceWorkerGenerator sourceWorkerGenerator = null;
                    if (sourceWorker.equals("wormhole")) {
                        String liaisonSourceWorkerWormholeHost = commandLine.getOptionValue("LiaisonSourceWorkerWormholeHost");
                        int liaisonSourceWorkerWormholePort = commandLine.getOptionValue("LiaisonSourceWorkerWormholePort");

                        sourceWorkerGenerator = new SourceWorkerGenerator() {
                            @Override
                            protected RahabLiaisonSourceWorker generateWorker() {
                                return new RahabLiaisonSourceWorkerAsWormhole(liaisonSourceWorkerWormholeHost, liaisonSourceWorkerWormholePort);
                            }
                        };
                    }

                    String liaisonBrokerHost = commandLine.getOptionValue("LiaisonBrokerHost");
                    int liaisonBrokerPort = commandLine.getOptionValue("LiaisonBrokerPort");
                    runAsLiaisonSource(liaisonBrokerHost, liaisonBrokerPort, sourceWorkerGenerator);
                    return;
            }
        }

        StringBuilder builder = new StringBuilder();
        mainCLI.usage(builder, "java -jar");
        mainLogger.print(builder.toString());

        mainLogger.print("===== SAMPLE =====");
        mainLogger.print("# Run as HTTP Proxy, listen on port 1080");
        mainLogger.print("java -jar Rahab-" + VERSION + ".jar --mode HttpProxy --port 1080");
        mainLogger.print("# Run as Wormhole, listen on port 1081, destination is 127.0.0.1:1080, data without transform");
        mainLogger.print("java -jar Rahab-" + VERSION + ".jar --mode Wormhole --port [1081] --WormholeName [SAMPLE] --WormholeDestinationHost [127.0.0.1] --WormholeDestinationPort [1080]");
        mainLogger.print("# Run as Wormhole, listen on port 1081, destination is 127.0.0.1:1080, use transformer HttpServer");
        mainLogger.print("java -jar Rahab-" + VERSION + ".jar --mode Wormhole --port [1081] --WormholeName [SAMPLE] --WormholeDestinationHost [127.0.0.1] --WormholeDestinationPort [1080] --WormholeTransformer HttpServer");
        mainLogger.print("# Run as Wormhole, listen on port 1081, destination is 127.0.0.1:1080, use transformer HttpClient with fake host api.com");
        mainLogger.print("java -jar Rahab-" + VERSION + ".jar --mode Wormhole --port [1081] --WormholeName [SAMPLE] --WormholeDestinationHost [127.0.0.1] --WormholeDestinationPort [1080] --WormholeTransformer HttpClient --WormholeTransformerHttpFakeHost api.com");
        mainLogger.print("# Run as LiaisonBroker, listen on port 1082");
        mainLogger.print("java -jar Rahab-" + VERSION + ".jar --mode LiaisonBroker --port 1082");
        mainLogger.print("# Run as LiaisonSource, target broker is 127.0.0.1:1082, with worker as Wormhole (destination is 192.168.0.1:1080)");
        mainLogger.print("java -jar Rahab-" + VERSION + ".jar --mode LiaisonSource --LiaisonBrokerHost 127.0.0.1 --LiaisonBrokerPort 1082 --LiaisonSourceWorker wormhole --LiaisonSourceWorkerWormholeHost 192.168.0.1 --LiaisonSourceWorkerWormholePort 1080");


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
                    Keel.getVertx().close();
                });
    }

    private static void runAsLiaisonBroker(int port) {
        KeelLogger mainLogger = Keel.outputLogger("RahabMain");

        new RahabLiaisonBroker()
                .listen(port)
                .onComplete(netServerAsyncResult -> {
                    if (netServerAsyncResult.failed()) {
                        mainLogger.exception("RahabProxyBroker 启动失败", netServerAsyncResult.cause());
                        Keel.getVertx().close();
                    } else {
                        mainLogger.notice("RahabProxyBroker 启动成功 端口 " + port);
                    }
                });
    }

    private static void runAsLiaisonSource(String liaisonBrokerHost, int liaisonBrokerPort, SourceWorkerGenerator sourceWorkerGenerator) {
        KeelLogger mainLogger = Keel.outputLogger("RahabMain");

        RahabLiaisonSource rahabLiaisonSource = new RahabLiaisonSource("LiaisonSource");
        rahabLiaisonSource.setSourceWorkerGenerator(sourceWorkerGenerator);
        rahabLiaisonSource.start(liaisonBrokerHost, liaisonBrokerPort)
                .onComplete(netServerAsyncResult -> {
                    if (netServerAsyncResult.failed()) {
                        mainLogger.exception("RahabLiaisonSource 启动失败", netServerAsyncResult.cause());
                        Keel.getVertx().close();
                    } else {
                        mainLogger.notice("RahabLiaisonSource 启动成功 掮客 地址 " + liaisonBrokerHost + "端口 " + liaisonBrokerPort);
                    }
                });
    }
}
