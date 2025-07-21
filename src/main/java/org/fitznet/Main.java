package org.fitznet;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.fitznet.listener.LoginListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class Main {

    @Value("${discord.bot.token}")
    private String botToken;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    public JDA discordBotClient() throws InterruptedException {
        JDA jda = JDABuilder.createDefault(botToken)
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.watching("The server... at all times"))
                .build().awaitReady();

        // Add the LoginListener after JDA is created and ready
        jda.addEventListener(new LoginListener(jda));

        return jda;
    }

    @Bean
    public BotController botController(JDA jda, @Value("${discord.bot.token}") String token) {
        return new BotController(jda, token);
    }
}