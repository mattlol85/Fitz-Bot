package org.fitznet;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.fitznet.listener.LoginListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bot")
public class BotController {
    private JDA jda;
    private final String token;

    public BotController(JDA jda, @Value("${discord.bot.token}") String token) {
        this.jda = jda;
        this.token = token;
    }

    @PostMapping("/startup")
    public String startup() {
        if (jda == null || jda.getStatus() == JDA.Status.SHUTDOWN) {
            try {
                jda = JDABuilder.createDefault(token)
                        .setStatus(OnlineStatus.ONLINE)
                        .setActivity(Activity.watching("The server... at all times"))
                        .build().awaitReady();

                // Add the LoginListener after JDA is ready
                jda.addEventListener(new LoginListener(jda));

                return "Bot started successfully.";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Failed to start bot: " + e.getMessage();
            }
        }
        return "Bot is already running.";
    }

    @PostMapping("/shutdown")
    public String shutdown() {
        if (jda != null && jda.getStatus() != JDA.Status.SHUTDOWN) {
            jda.shutdown();
            return "Bot is shutting down.";
        }
        return "Bot is already shut down.";
    }

    @GetMapping("/status")
    public String getStatus() {
        if (jda == null) {
            return "Bot is not initialized.";
        }
        return "Bot status: " + jda.getStatus();
    }
}
