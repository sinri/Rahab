package io.github.sinri.Rahab.v2.executive;

import io.github.sinri.Rahab.v2.wormhole.liaison.RahabLiaisonSource;
import io.github.sinri.Rahab.v2.wormhole.liaison.RahabLiaisonSourceWorker;
import io.github.sinri.Rahab.v2.wormhole.liaison.SourceWorkerGenerator;
import io.github.sinri.Rahab.v2.wormhole.liaison.impl.RahabLiaisonSourceWorkerAsWormhole;
import io.github.sinri.keel.Keel;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.TypedOption;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LiaisonSourceExecutor extends RahabExecutor {
    public LiaisonSourceExecutor(List<String> userCommandLineArguments) {
        super(userCommandLineArguments);
    }

    @Override
    protected CLI getCommandLineParseRule() {
        return getSharedCommandParseRule()
                .addOption(new Option()
                        .setLongName("name")
                        .setDefaultValue(UUID.randomUUID().toString())
                        .setDescription("情报源名称")
                )
                .addOption(new Option()
                        .setLongName("worker")
                        .setChoices(Set.of("wormhole"))
                        .setDefaultValue("wormhole")
                        .setDescription("情报源的工作方式")
                )
                .addOption(new Option()
                        .setLongName("workerWormholeHost")
                        .setDefaultValue("127.0.0.1")
                        .setDescription("情报源使用的掮客服务的远程地址")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setLongName("workerWormholePort")
                        .setDefaultValue(String.valueOf(7999))
                        .setDescription("情报源使用的掮客服务的远程端口")
                )
                .addOption(new Option()
                        .setLongName("brokerHost")
                        .setDefaultValue("127.0.0.1")
                        .setDescription("情报源使用的掮客服务的远程地址")
                )
                .addOption(new TypedOption<Integer>()
                        .setType(Integer.class)
                        .setLongName("brokerPort")
                        .setDefaultValue(String.valueOf(7999))
                        .setDescription("情报源使用的掮客服务的远程端口")
                );
    }

    @Override
    protected void execute(CommandLine commandLine) {
        String sourceName = commandLine.getOptionValue("name");

        String sourceWorker = commandLine.getOptionValue("worker");
        SourceWorkerGenerator sourceWorkerGenerator = null;
        if (sourceWorker.equals("wormhole")) {
            String liaisonSourceWorkerWormholeHost = commandLine.getOptionValue("workerWormholeHost");
            int liaisonSourceWorkerWormholePort = commandLine.getOptionValue("workerWormholePort");

            sourceWorkerGenerator = new SourceWorkerGenerator() {
                @Override
                protected RahabLiaisonSourceWorker generateWorker() {
                    return new RahabLiaisonSourceWorkerAsWormhole(liaisonSourceWorkerWormholeHost, liaisonSourceWorkerWormholePort);
                }
            };
        }

        String liaisonBrokerHost = commandLine.getOptionValue("brokerHost");
        int liaisonBrokerPort = commandLine.getOptionValue("brokerPort");

        RahabLiaisonSource rahabLiaisonSource = new RahabLiaisonSource(sourceName);
        rahabLiaisonSource.setSourceWorkerGenerator(sourceWorkerGenerator);
        rahabLiaisonSource.start(liaisonBrokerHost, liaisonBrokerPort)
                .onComplete(netServerAsyncResult -> {
                    if (netServerAsyncResult.failed()) {
                        getLogger().exception("RahabLiaisonSource 启动失败", netServerAsyncResult.cause());
                        Keel.getVertx().close();
                    } else {
                        getLogger().notice("RahabLiaisonSource 启动成功 掮客 地址 " + liaisonBrokerHost + "端口 " + liaisonBrokerPort);
                    }
                });
    }
}
