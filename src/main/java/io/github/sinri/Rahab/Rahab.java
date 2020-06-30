package io.github.sinri.Rahab;

import io.github.sinri.Rahab.kit.RahabAgent;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.TypedOption;

import java.util.Arrays;

public class Rahab {
    public static void main(String[] args) {
        System.out.println("By faith the harlot Rahab perished not with them that believed not, when she had received the spies with peace. (Hebrews 11:31)");

        CLI cli = CLI.create("Rahab")
                .setSummary("Tear the wall of Jericho down!")
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
                                .setRequired(true)
                                .setType(Integer.class)
                )
                .addOption(
                        new TypedOption<String>()
                                .setLongName("mode")
                                .setShortName("m")
                                .setDescription("Running Mode: LOCAL / REMOTE")
                                .setRequired(true)
                                .setType(String.class)
                )
                .addOption(
                        new TypedOption<String>()
                                .setLongName("remote-address")
                                .setShortName("r")
                                .setDescription("Needed in LOCAL mode, IP or domain")
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
                );

        try{
            CommandLine commandLine = cli.parse(Arrays.asList(args));
            if (!commandLine.isValid() && commandLine.isAskingForHelp()) {
                throw new Exception("Emmm...");
            }

            RahabAgent.setPort(commandLine.getOptionValue("listen-port"));
            RahabAgent.setMode(commandLine.getOptionValue("mode"));
            RahabAgent.setRemoteAddress(commandLine.getOptionValue("remote-address"));
            RahabAgent.setRemotePort(commandLine.getOptionValue("remote-port"));

            RahabAgent.initializeVertx(commandLine.getOptionValue("pool-size"));
        }catch (Exception e){
            StringBuilder builder = new StringBuilder();
            cli.usage(builder);
            System.err.println(builder.toString());
            System.exit(1);
        }

        (new RahabAgent()).run();
    }
}
