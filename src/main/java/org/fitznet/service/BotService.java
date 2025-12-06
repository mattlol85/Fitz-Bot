package org.fitznet.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.fitznet.commands.ConfigCommands;
import org.fitznet.commands.JoenetCommands;
import org.fitznet.commands.MilkCommands;
import org.fitznet.listener.LoginListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Service for managing Discord bot lifecycle operations.
 */
@Service
@Slf4j
public class BotService {

    @Value("${discord.bot.token}")
    private String token;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JoenetCommands joenetCommands;

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
            jda.addEventListener(new LoginListener());
            jda.addEventListener(new ConfigCommands());
            jda.addEventListener(joenetCommands);
            jda.addEventListener(new MilkCommands());

            // Small delay to ensure guild cache is fully populated
            Thread.sleep(2000);

            // Register commands for all current guilds immediately
            if (jda.getGuilds().isEmpty()) {
                log.warn("No guilds found after startup - guild cache may not be ready yet");
            }

            // Combine commands from all handlers
            var allCommands = new java.util.ArrayList<>(java.util.Arrays.asList(ConfigCommands.getCommands()));
            allCommands.addAll(java.util.Arrays.asList(JoenetCommands.getCommands()));
            allCommands.addAll(java.util.Arrays.asList(MilkCommands.getCommands()));

            for (var guild : jda.getGuilds()) {
                guild.updateCommands()
                    .addCommands(allCommands)
                    .queue(
                        success -> log.info("Commands registered for guild: {} ({})", guild.getName(), guild.getId()),
                        error -> log.error("Failed to register commands for guild {}: {}", guild.getName(), error.getMessage())
                    );
            }

            // Also register globally for new servers (backup)
            jda.updateCommands().addCommands(allCommands).queue(
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
     * Stops the Discord bot and shuts down the entire application.
     *
     * @return shutdown result message
     */
    public String stopBot() {
        if (jda != null && jda.getStatus() != JDA.Status.SHUTDOWN) {
            log.info("Shutting down Discord bot...");
            jda.shutdown();
            jda = null;
            log.info("Bot has been shut down");
        }

        log.info("Shutting down Spring Boot application...");

        // Shutdown the entire Spring Boot application
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Give time for the response to be sent
                SpringApplication.exit(applicationContext, () -> 0);
                System.exit(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted during application shutdown", e);
                System.exit(1);
            }
        }).start();

        return "Bot and application are shutting down completely.";
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

        // Combine commands from all handlers
        var allCommands = new java.util.ArrayList<>(java.util.Arrays.asList(ConfigCommands.getCommands()));
        allCommands.addAll(java.util.Arrays.asList(JoenetCommands.getCommands()));
        allCommands.addAll(java.util.Arrays.asList(MilkCommands.getCommands()));

        int guildCount = 0;
        for (var guild : jda.getGuilds()) {
            guild.updateCommands()
                .addCommands(allCommands)
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
            // Combine commands from all handlers
            var allCommands = new java.util.ArrayList<>(java.util.Arrays.asList(ConfigCommands.getCommands()));
            allCommands.addAll(java.util.Arrays.asList(joenetCommands.getCommands()));
            allCommands.addAll(java.util.Arrays.asList(MilkCommands.getCommands()));

            var guild = jda.getGuildById(guildId);
            if (guild == null) {
                return "Guild not found with ID: " + guildId;
            }

            guild.updateCommands()
                .addCommands(allCommands)
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

    /**
     * Resets all join counts for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @return reset result message
     */
    public String resetGuildJoinCounts(String guildId) {
        if (jda == null) {
            return "Bot is not running!";
        }

        try {
            long guildIdLong = Long.parseLong(guildId);

            // Verify guild exists
            var guild = jda.getGuildById(guildIdLong);
            if (guild == null) {
                return "Guild not found with ID: " + guildId;
            }

            // Get LoginListener from JDA event listeners
            LoginListener loginListener = getLoginListener();
            if (loginListener == null) {
                return "LoginListener not found - bot may not be properly initialized";
            }

            int resetCount = loginListener.resetAllJoinCounts(guildIdLong);
            String result = String.format("Reset join counts for %d users in guild '%s' (%s)",
                                        resetCount, guild.getName(), guildId);
            log.info(result);
            return result;

        } catch (NumberFormatException e) {
            return "Invalid guild ID format: " + guildId;
        } catch (Exception e) {
            String error = "Error resetting join counts for guild " + guildId + ": " + e.getMessage();
            log.error(error, e);
            return error;
        }
    }

    /**
     * Gets the LoginListener from the JDA event listeners.
     *
     * @return LoginListener instance or null if not found
     */
    private LoginListener getLoginListener() {
        if (jda == null) {
            return null;
        }

        return jda.getRegisteredListeners().stream()
                .filter(listener -> listener instanceof LoginListener)
                .map(listener -> (LoginListener) listener)
                .findFirst()
                .orElse(null);
    }

}
