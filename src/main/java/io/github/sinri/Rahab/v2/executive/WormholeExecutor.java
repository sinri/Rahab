package io.github.sinri.Rahab.v2.executive;

import io.github.sinri.Rahab.v2.wormhole.Wormhole;
import io.github.sinri.Rahab.v2.wormhole.transform.impl.http.client.TransformerFromHttpRequestToRaw;
import io.github.sinri.Rahab.v2.wormhole.transform.impl.http.client.TransformerFromRawToHttpRequest;
import io.github.sinri.Rahab.v2.wormhole.transform.impl.http.server.TransformerFromHttpResponseToRaw;
import io.github.sinri.Rahab.v2.wormhole.transform.impl.http.server.TransformerFromRawToHttpResponse;
import io.github.sinri.keel.Keel;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.TypedOption;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class WormholeExecutor extends RahabExecutor {
    public WormholeExecutor(List<String> userCommandLineArguments) {
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
                .addOption(new Option()
                        .setLongName("name")
                        .setDefaultValue(UUID.randomUUID().toString())
                        .setDescription("虫洞的名称")
                )
                .addOption(new Option()
                        .setLongName("transformer")
                        .setChoices(Set.of("HttpServer", "HttpClient"))
                        .setDefaultValue("")
                        .setDescription("作为虫洞运行时使用的数据混淆器")
                )
                .addOption(new Option()
                        .setLongName("transformerHttpFakeHost")
                        .setDefaultValue("cloud.player.com")
                        .setDescription("作为虫洞运行时,使用数据混淆器【HttpClient】时指定一个伪装的域名")
                )
                .addOption(new Option()
                        .setLongName("destinationHost")
                        .setDefaultValue("127.0.0.1")
                        .setDescription("作为虫洞运行时使用的远程地址")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setLongName("destinationPort")
                        .setDefaultValue(String.valueOf(7999))
                        .setDescription("作为虫洞运行时使用的远程端口")
                );
    }

    @Override
    protected void execute(CommandLine commandLine) {
        int port = commandLine.getOptionValue("port");

        String wormholeName = commandLine.getOptionValue("name");
        String destinationHost = commandLine.getOptionValue("destinationHost");
        int destinationPort = commandLine.getOptionValue("destinationPort");

        String wormholeTransformerCode = commandLine.getOptionValue("transformer");

        Wormhole wormhole = new Wormhole(wormholeName, destinationHost, destinationPort);
        if (wormholeTransformerCode.equals("HttpServer")) {
            wormhole
                    .setTransformerForDataFromRemote(new TransformerFromRawToHttpResponse())
                    .setTransformerForDataFromLocal(new TransformerFromHttpRequestToRaw());
        } else if (wormholeTransformerCode.equals("HttpClient")) {
            String fakeHost = commandLine.getOptionValue("transformerHttpFakeHost");
            wormhole
                    .setTransformerForDataFromRemote(new TransformerFromHttpResponseToRaw())
                    .setTransformerForDataFromLocal(new TransformerFromRawToHttpRequest(fakeHost));
        }

        wormhole.listen(port)
                .onSuccess(server -> {
                    getLogger().notice("WormholeProxy [" + wormholeName + "] 端口 " + port + " 开始运作，终点 " + destinationHost + ":" + destinationPort);
                })
                .onFailure(throwable -> {
                    getLogger().exception("WormholeProxy [" + wormholeName + "] 端口 " + port + " 启动失败", throwable);
                    Keel.getVertx().close();
                });
    }
}
