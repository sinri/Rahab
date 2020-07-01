package io.github.sinri.Rahab;

import io.github.sinri.Rahab.kit.RahabAgent;
import io.github.sinri.Rahab.logger.RahabLogger;
import io.github.sinri.Rahab.logger.RahabLoggerDelegate;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.TypedOption;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

public class Rahab {
    public static boolean vertxLoggingOnly = false;
    public static void main(String[] args) {
        RahabLogger.getLogger(Rahab.class).info("By faith the harlot Rahab perished not with them that believed not, when she had received the spies with peace. (Hebrews 11:31)");

        CLI cli = CLI.create("Rahab")
                .setSummary("Tear the wall of Jericho down!")
                .addOption(
                        new TypedOption<String>()
                                .setLongName("config-file")
                                .setShortName("c")
                                .setDescription("给定配置文件，否则使用默认配置。")
                                .setDefaultValue("/app/config/rahab-remote.yml")
                                .setRequired(false)
                                .setType(String.class)
                )
                .addOption(
                        new TypedOption<Integer>()
                                .setLongName("pool-size")
                                .setShortName("s")
                                .setDescription("Pool Size")
                                .setRequired(false)
                                .setDefaultValue("10")
                                .setType(Integer.class)
                )
                .addOption(
                        new TypedOption<Integer>()
                                .setLongName("listen-port")
                                .setShortName("p")
                                .setDescription("Listen Port")
                                .setDefaultValue("80")
                                .setRequired(false)
                                .setType(Integer.class)
                )
                .addOption(
                        new TypedOption<String>()
                                .setLongName("mode")
                                .setShortName("m")
                                .setDescription("Running Mode: LOCAL / REMOTE")
                                .setDefaultValue("UNKNOWN")
                                .setRequired(false)
                                .setType(String.class)
                )
                .addOption(
                        new TypedOption<String>()
                                .setLongName("remote-address")
                                .setShortName("r")
                                .setDescription("Needed in LOCAL mode, IP or domain")
                                .setDefaultValue("127.0.0.1")
                                .setRequired(false)
                                .setType(String.class)
                )
                .addOption(
                        new TypedOption<Integer>()
                                .setLongName("remote-port")
                                .setShortName("e")
                                .setDescription("Needed in LOCAL mode, port")
                                .setDefaultValue("80")
                                .setRequired(false)
                                .setType(Integer.class)
                )
                .addOption(new Option()
                        .setLongName("use-vertx-log-only")
                        .setShortName("v")
                        .setDescription("Only use vertx standard logging")
                        .setFlag(true))
//                .addOption(new Option()
//                        .setLongName("use-debug-log")
//                        .setShortName("d")
//                        .setDescription("enables debug logging support")
//                        .setFlag(true))
                ;

        try {
            CommandLine commandLine = cli.parse(Arrays.asList(args));
            if (!commandLine.isValid() && commandLine.isAskingForHelp()) {
                throw new Exception("Emmm...");
            }

            String configFile = commandLine.getOptionValue("config-file");
            RahabLogger.getLogger(Rahab.class).info("Target Config File: " + configFile);
            if (
                    configFile != null
                            && !configFile.equalsIgnoreCase("")
                            && (new File(configFile)).exists()
            ) {
                // read config
                RahabLogger.getLogger(Rahab.class).info("Opening Config File: " + configFile);
                InputStream input = new FileInputStream(new File(configFile));
                Yaml yaml = new Yaml();
                Map<String, Object> loadedYaml = yaml.load(input);
                RahabAgent.setPort((Integer) loadedYaml.getOrDefault("listen-port", 80));
                RahabAgent.setMode((String) loadedYaml.getOrDefault("mode", "UNKNOWN"));
                RahabAgent.setRemoteAddress((String) loadedYaml.getOrDefault("remote-address", "127.0.0.1"));
                RahabAgent.setRemotePort((Integer) loadedYaml.getOrDefault("remote-port", 80));
            } else {
                RahabLogger.getLogger(Rahab.class).info("No valid Config File, try command line");

                RahabAgent.setPort(commandLine.getOptionValue("listen-port"));
                RahabAgent.setMode(commandLine.getOptionValue("mode"));
                RahabAgent.setRemoteAddress(commandLine.getOptionValue("remote-address"));
                RahabAgent.setRemotePort(commandLine.getOptionValue("remote-port"));
            }

            vertxLoggingOnly = commandLine.isFlagEnabled("use-vertx-log-only");
            //RahabLoggerDelegate.debugModeOpened=commandLine.isFlagEnabled("use-debug-log");
            RahabLoggerDelegate.debugModeOpened = true;

            RahabLogger.getLogger(Rahab.class).info("Config Loaded");
            RahabAgent.initializeVertx(commandLine.getOptionValue("pool-size"));

            RahabLogger.getLogger(Rahab.class).info("Running Now");
        }catch (Exception e){
            StringBuilder builder = new StringBuilder();
            cli.usage(builder);
            RahabLogger.getLogger(Rahab.class).error(builder.toString());
            System.exit(1);
        }

        (new RahabAgent()).run();
    }
}
