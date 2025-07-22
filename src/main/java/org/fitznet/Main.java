package org.fitznet;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.fitznet.util.JdaConfiguration;
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
        return JdaConfiguration.createConfiguredJda(botToken);
    }

    @Bean
    public BotController botController(JDA jda, @Value("${discord.bot.token}") String token) {
        return new BotController(jda, token);
    }
}