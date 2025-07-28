package org.fitznet.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.fitznet.commands.ConfigCommands;
import org.fitznet.listener.LoginListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for managing Discord bot lifecycle operations.
 */
@Service
@Slf4j
public class BotService {

    @Value("${discord.bot.token}")
    private String token;

    /**
     * -- GETTER --
     *  Gets the JDA instance.
     *
     */
    @Getter
    private JDA jda;

    /**
     * Starts the Discord bot with full initialization.
     *
     * @return startup result message
     */
    public String startBot() {
        if (jda != null && jda.getStatus() != JDA.Status.SHUTDOWN) {
            return "Bot is already running.";
        }

        try {
            log.info("Starting Discord bot...");

            jda = JDABuilder.createDefault(token)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.watching("The server... at all times"))
                    .build().awaitReady();

            // Add event listeners
            jda.addEventListener(new LoginListener(jda));
            jda.addEventListener(new ConfigCommands());

            // Small delay to ensure guild cache is fully populated
            Thread.sleep(2000);

            // Register commands for all current guilds immediately
            if (jda.getGuilds().isEmpty()) {
                log.warn("No guilds found after startup - guild cache may not be ready yet");
            }

            for (var guild : jda.getGuilds()) {
                guild.updateCommands()
                    .addCommands(ConfigCommands.getCommands())
                    .queue(
                        success -> log.info("Commands registered for guild: {} ({})", guild.getName(), guild.getId()),
                        error -> log.error("Failed to register commands for guild {}: {}", guild.getName(), error.getMessage())
                    );
            }

            // Also register globally for new servers (backup)
            jda.updateCommands().addCommands(ConfigCommands.getCommands()).queue(
                success -> log.info("Global commands registered successfully"),
                error -> log.error("Failed to register global commands: {}", error.getMessage())
            );

            String result = String.format("Bot started successfully! Commands registered for %d guilds immediately.", jda.getGuilds().size());
            log.info(result);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String error = "Failed to start bot: " + e.getMessage();
            log.error(error);
            return error;
        } catch (Exception e) {
            String error = "Unexpected error during bot startup: " + e.getMessage();
            log.error(error, e);
            return error;
        }
    }

    /**
     * Stops the Discord bot.
     *
     * @return shutdown result message
     */
    public String stopBot() {
        if (jda != null && jda.getStatus() != JDA.Status.SHUTDOWN) {
            jda.shutdown();
            jda = null;
            log.info("Bot has been shut down");
            return "Bot is shutting down.";
        }
        return "Bot is already shut down.";
    }

    /**
     * Forces command updates for all guilds.
     *
     * @return force update result message
     */
    public String forceUpdateCommands() {
        if (jda == null) {
            return "Bot is not running!";
        }

        int guildCount = 0;
        for (var guild : jda.getGuilds()) {
            guild.updateCommands()
                .addCommands(ConfigCommands.getCommands())
                .queue(
                    success -> log.info("Force updated commands for guild: {}", guild.getName()),
                    error -> log.error("Failed to force update commands for guild {}: {}", guild.getName(), error.getMessage())
                );
            guildCount++;
        }

        return String.format("Force updated commands for %d guilds. Commands should appear immediately!", guildCount);
    }

    /**
     * Registers commands for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @return registration result message
     */
    public String registerGuildCommands(String guildId) {
        if (jda == null) {
            return "Bot is not running!";
        }

        try {
            jda.getGuildById(guildId).updateCommands()
                .addCommands(ConfigCommands.getCommands())
                .queue(
                    success -> log.info("Guild commands registered successfully for guild {}", guildId),
                    error -> log.error("Failed to register guild commands for {}: {}", guildId, error.getMessage())
                );
            return "Guild commands registered for " + guildId + " (should appear immediately)";
        } catch (Exception e) {
            return "Error registering guild commands: " + e.getMessage();
        }
    }

    /**
     * Gets the current bot status.
     *
     * @return status message
     */
    public String getStatus() {
        if (jda == null) {
            return "Bot is not initialized.";
        }
        return "Bot status: " + jda.getStatus();
    }

}
