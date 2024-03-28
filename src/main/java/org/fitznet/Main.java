package org.fitznet;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.fitznet.listener.LoginListener;

@Slf4j
public class Main {
    static JDA discordBotClient;
    protected static String discordToken;
    public static void main(String[] args) throws InterruptedException {
        // Using Command line args/Direct
        if (System.getenv("DISCORD_TOKEN") == null){
         discordToken = args.length != 0 ? args[0] : "KEY_HERE";
        }
        //Using env vars
        discordToken = System.getenv("DISCORD_TOKEN");

        System.out.println(System.getenv("DISCORD_TOKEN"));
        setupBotClient(discordToken);
    }

    static void setupBotClient(String token) throws InterruptedException {
        discordBotClient = JDABuilder.createDefault(token)
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.watching("The server... at all times"))
                .build();

        discordBotClient.addEventListener(new LoginListener(discordBotClient));
        discordBotClient.awaitReady();
    }
}