package org.fitznet;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

@Slf4j
public class Main {
    static JDA discordBotClient;
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 0) {
            log.info("Starting ");
            discordBotClient = JDABuilder.createDefault(args[0])
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.watching("The server... at all times"))
                    .build();
        } else {
            discordBotClient = JDABuilder.createDefault("KEY_HERE")
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.watching("The server... at all times"))
                    .build();
        }


        discordBotClient.addEventListener(new LoginListener(discordBotClient));
        discordBotClient.awaitReady();
    }
}