package org.fitznet;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.fitznet.service.BotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class Main implements CommandLineRunner {

    @Autowired
    private BotService botService;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("Starting Discord bot automatically...");
        botService.startBot();
    }

    @Bean
    public JDA jda() {
        return botService != null ? botService.getJda() : null;
    }

}