package org.fitznet.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.fitznet.data.GuildConfigDatabase;

/**
 * Slash command handler for bot configuration commands.
 */
@Slf4j
public class ConfigCommands extends ListenerAdapter {
    private final GuildConfigDatabase configDatabase;

    public ConfigCommands() {
        try {
            this.configDatabase = new GuildConfigDatabase();
            log.info("ConfigCommands initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize ConfigCommands", e);
            throw e;
        }
    }

    /**
     * Gets the slash command definitions.
     */
    public static SlashCommandData[] getCommands() {
        return new SlashCommandData[]{
            Commands.slash("setbotchannel", "Set the channel for bot milestone messages")
                .addOption(OptionType.CHANNEL, "channel", "The text channel to use for bot messages", true),
            Commands.slash("getbotchannel", "Show the current bot channel configuration"),
            Commands.slash("resetjoincounts", "Reset all voice join counts for this server (Admin only)"),
            Commands.slash("initializetracking", "Initialize tracking date for this server (Admin only)")
        };
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        log.info("Received slash command: {} from user: {} in guild: {}",
                event.getName(),
                event.getUser().getName(),
                event.getGuild() != null ? event.getGuild().getName() : "DM");

        try {
            // Immediately acknowledge to prevent timeout
            if (!event.isAcknowledged()) {
                event.deferReply(true).queue();
            }

            if (!event.isFromGuild()) {
                event.getHook().editOriginal("❌ This command can only be used in a server!").queue();
                return;
            }

            switch (event.getName()) {
                case "setbotchannel" -> handleSetBotChannel(event);
                case "getbotchannel" -> handleGetBotChannel(event);
                case "resetjoincounts" -> handleResetJoinCounts(event);
                case "initializetracking" -> handleInitializeTracking(event);
                default -> {
                    log.warn("Unknown command: {}", event.getName());
                    event.getHook().editOriginal("❌ Unknown command").queue();
                }
            }
        } catch (Exception e) {
            log.error("Error in onSlashCommandInteraction for command: {}", event.getName(), e);
            try {
                if (event.isAcknowledged()) {
                    event.getHook().editOriginal("❌ An error occurred while processing your command. Please try again.").queue();
                } else {
                    event.reply("❌ An error occurred while processing your command. Please try again.")
                            .setEphemeral(true).queue();
                }
            } catch (Exception replyError) {
                log.error("Failed to send error response", replyError);
            }
        }
    }

    private void handleSetBotChannel(SlashCommandInteractionEvent event) {
        log.info("Processing setbotchannel command for guild: {}", event.getGuild().getName());

        try {
            // Check permissions
            if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
                log.warn("User {} lacks MANAGE_SERVER permission", event.getUser().getName());
                event.getHook().editOriginal("❌ You need the 'Manage Server' permission to use this command!").queue();
                return;
            }

            // Get channel option
            var channelOption = event.getOption("channel");
            if (channelOption == null) {
                log.warn("No channel option provided");
                event.getHook().editOriginal("❌ No channel provided!").queue();
                return;
            }

            // Validate channel type
            var channel = channelOption.getAsChannel();
            log.info("Channel selected: {} (type: {})", channel.getName(), channel.getType());

            if (!(channel instanceof TextChannel)) {
                log.warn("Non-text channel selected: {} (type: {})", channel.getName(), channel.getType());
                event.getHook().editOriginal("❌ Please select a text channel, not a voice channel or category!").queue();
                return;
            }

            TextChannel textChannel = (TextChannel) channel;
            long guildId = event.getGuild().getIdLong();
            long channelId = textChannel.getIdLong();

            log.info("Saving bot channel config: guild={}, channel={} ({})", guildId, channelId, textChannel.getName());

            // Save to database
            configDatabase.setBotChannelId(guildId, channelId);

            log.info("Successfully saved bot channel config for guild {}", guildId);

            // Send success response
            event.getHook().editOriginal("✅ Bot channel set to " + textChannel.getAsMention() +
                       "\nMilestone messages will now be sent here!").queue();

        } catch (Exception e) {
            log.error("Error in handleSetBotChannel", e);
            event.getHook().editOriginal("❌ An error occurred while setting the bot channel: " + e.getMessage()).queue();
        }
    }

    private void handleGetBotChannel(SlashCommandInteractionEvent event) {
        log.info("Processing getbotchannel command for guild: {}", event.getGuild().getName());

        try {
            long guildId = event.getGuild().getIdLong();
            Long channelId = configDatabase.getBotChannelId(guildId);

            if (channelId == null) {
                log.info("No bot channel configured for guild {}", guildId);
                event.getHook().editOriginal("ℹ️ No bot channel is currently configured.\nUse `/setbotchannel` to set one!").queue();
                return;
            }

            TextChannel channel = event.getGuild().getTextChannelById(channelId);
            if (channel == null) {
                log.warn("Configured bot channel {} not found in guild {}", channelId, guildId);
                event.getHook().editOriginal("⚠️ Bot channel was set to ID `" + channelId + "` but that channel no longer exists.\nUse `/setbotchannel` to set a new one!").queue();
                return;
            }

            log.info("Bot channel for guild {} is {}", guildId, channel.getName());
            event.getHook().editOriginal("ℹ️ Bot channel is currently set to " + channel.getAsMention()).queue();

        } catch (Exception e) {
            log.error("Error in handleGetBotChannel", e);
            event.getHook().editOriginal("❌ An error occurred while getting the bot channel: " + e.getMessage()).queue();
        }
    }

    private void handleResetJoinCounts(SlashCommandInteractionEvent event) {
        log.info("Processing resetjoincounts command for guild: {}", event.getGuild().getName());

        try {
            // Check permissions
            if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                log.warn("User {} lacks ADMINISTRATOR permission", event.getUser().getName());
                event.getHook().editOriginal("❌ You need the 'Administrator' permission to use this command!").queue();
                return;
            }

            long guildId = event.getGuild().getIdLong();

            // Reset join counts in the database
            int resetCount = configDatabase.resetAllJoinCounts(guildId);

            log.info("Successfully reset join counts for {} users in guild {}", resetCount, guildId);

            // Send success response
            event.getHook().editOriginal(String.format("✅ Reset join counts for %d users in this server!", resetCount)).queue();

        } catch (Exception e) {
            log.error("Error in handleResetJoinCounts", e);
            event.getHook().editOriginal("❌ An error occurred while resetting join counts: " + e.getMessage()).queue();
        }
    }

    private void handleInitializeTracking(SlashCommandInteractionEvent event) {
        log.info("Processing initializetracking command for guild: {}", event.getGuild().getName());

        try {
            // Check permissions
            if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                log.warn("User {} lacks ADMINISTRATOR permission", event.getUser().getName());
                event.getHook().editOriginal("❌ You need the 'Administrator' permission to use this command!").queue();
                return;
            }

            long guildId = event.getGuild().getIdLong();

            // Check if tracking is already initialized
            if (configDatabase.isTrackingInitialized(guildId)) {
                java.time.LocalDateTime startDate = configDatabase.getTrackingStartDate(guildId);
                String formattedDate = startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                event.getHook().editOriginal("ℹ️ Tracking is already initialized for this server since " + formattedDate + ".").queue();
                return;
            }

            // Force initialize tracking date in the database
            configDatabase.forceInitializeTracking(guildId);

            java.time.LocalDateTime startDate = configDatabase.getTrackingStartDate(guildId);
            String formattedDate = startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"));

            log.info("Successfully initialized tracking date for guild {}", guildId);

            // Send success response
            event.getHook().editOriginal("✅ Tracking date initialized for this server starting " + formattedDate + "!\n" +
                    "Milestone messages will now include the tracking start date.").queue();

        } catch (Exception e) {
            log.error("Error in handleInitializeTracking", e);
            event.getHook().editOriginal("❌ An error occurred while initializing tracking date: " + e.getMessage()).queue();
        }
    }
}
