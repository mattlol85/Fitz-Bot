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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Slash command handler for bot configuration commands.
 */
@Slf4j
@Component
public class ConfigCommands extends ListenerAdapter {
    @Autowired
    private GuildConfigDatabase configDatabase;

    /**
     * Gets the slash command definitions.
     */
    public static SlashCommandData[] getCommands() {
        return new SlashCommandData[]{
                Commands.slash("setbotchannel", "Set the channel for bot milestone messages")
                        .addOption(OptionType.CHANNEL, "channel", "The text channel to use for bot messages", true),
                Commands.slash("getbotchannel", "Show the current bot channel configuration"),
                Commands.slash("setrequesterlogchannel", "Set the channel for requester logs (who requested what media)")
                        .addOption(OptionType.CHANNEL, "channel", "The text channel to use for requester logs", true),
                Commands.slash("getrequesterlogchannel", "Show the current requester log channel configuration"),
                Commands.slash("resetjoincounts", "Reset all voice join counts for this server (Admin only)"),
                Commands.slash("initializetracking", "Initialize tracking date for this server (Admin only)"),
                Commands.slash("currentcount", "Show current voice join counts for all users in this server")
        };
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Only handle commands that belong to this handler
        String commandName = event.getName();
        if (!isConfigCommand(commandName)) {
            return; // Let other handlers process this command
        }

        log.info("Received slash command: {} from user: {} in guild: {}",
                commandName,
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

            switch (commandName) {
                case "setbotchannel" -> handleSetBotChannel(event);
                case "getbotchannel" -> handleGetBotChannel(event);
                case "setrequesterlogchannel" -> handleSetRequesterLogChannel(event);
                case "getrequesterlogchannel" -> handleGetRequesterLogChannel(event);
                case "resetjoincounts" -> handleResetJoinCounts(event);
                case "initializetracking" -> handleInitializeTracking(event);
                case "currentcount" -> handleCurrentCount(event);
                default -> {
                    log.warn("Unknown config command: {}", commandName);
                    event.getHook().editOriginal("❌ Unknown command").queue();
                }
            }
        } catch (Exception e) {
            log.error("Error in onSlashCommandInteraction for command: {}", commandName, e);
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

    /**
     * Checks if the command name belongs to this handler.
     */
    private boolean isConfigCommand(String commandName) {
        return commandName.equals("setbotchannel") ||
               commandName.equals("getbotchannel") ||
               commandName.equals("setrequesterlogchannel") ||
               commandName.equals("getrequesterlogchannel") ||
               commandName.equals("resetjoincounts") ||
               commandName.equals("initializetracking") ||
               commandName.equals("currentcount");
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

            if (!(channel instanceof TextChannel textChannel)) {
                log.warn("Non-text channel selected: {} (type: {})", channel.getName(), channel.getType());
                event.getHook().editOriginal("❌ Please select a text channel, not a voice channel or category!").queue();
                return;
            }

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

    private void handleSetRequesterLogChannel(SlashCommandInteractionEvent event) {
        log.info("Processing setrequesterlogchannel command for guild: {}", event.getGuild().getName());

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

            if (!(channel instanceof TextChannel textChannel)) {
                log.warn("Non-text channel selected: {} (type: {})", channel.getName(), channel.getType());
                event.getHook().editOriginal("❌ Please select a text channel, not a voice channel or category!").queue();
                return;
            }

            long guildId = event.getGuild().getIdLong();
            long channelId = textChannel.getIdLong();

            log.info("Saving requester log channel config: guild={}, channel={} ({})", guildId, channelId, textChannel.getName());

            // Save to database
            configDatabase.setRequesterLogChannelId(guildId, channelId);

            log.info("Successfully saved requester log channel config for guild {}", guildId);

            // Send success response
            event.getHook().editOriginal("✅ Requester log channel set to " + textChannel.getAsMention() +
                    "\nRequester logs will now be sent here!").queue();

        } catch (Exception e) {
            log.error("Error in handleSetRequesterLogChannel", e);
            event.getHook().editOriginal("❌ An error occurred while setting the requester log channel: " + e.getMessage()).queue();
        }
    }

    private void handleGetRequesterLogChannel(SlashCommandInteractionEvent event) {
        log.info("Processing getrequesterlogchannel command for guild: {}", event.getGuild().getName());

        try {
            long guildId = event.getGuild().getIdLong();
            Long channelId = configDatabase.getRequesterLogChannelId(guildId);

            if (channelId == null) {
                log.info("No requester log channel configured for guild {}", guildId);
                event.getHook().editOriginal("ℹ️ No requester log channel is currently configured.\nUse `/setrequesterlogchannel` to set one!").queue();
                return;
            }

            TextChannel channel = event.getGuild().getTextChannelById(channelId);
            if (channel == null) {
                log.warn("Configured requester log channel {} not found in guild {}", channelId, guildId);
                event.getHook().editOriginal("⚠️ Requester log channel was set to ID `" + channelId + "` but that channel no longer exists.\nUse `/setrequesterlogchannel` to set a new one!").queue();
                return;
            }

            log.info("Requester log channel for guild {} is {}", guildId, channel.getName());
            event.getHook().editOriginal("ℹ️ Requester log channel is currently set to " + channel.getAsMention()).queue();

        } catch (Exception e) {
            log.error("Error in handleGetRequesterLogChannel", e);
            event.getHook().editOriginal("❌ An error occurred while getting the requester log channel: " + e.getMessage()).queue();
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

    private void handleCurrentCount(SlashCommandInteractionEvent event) {
        log.info("Processing currentcount command for guild: {}", event.getGuild().getName());

        try {
            long guildId = event.getGuild().getIdLong();

            // Retrieve current join counts from the database
            var joinCounts = configDatabase.getCurrentJoinCounts(guildId);

            if (joinCounts == null || joinCounts.isEmpty()) {
                log.info("No join counts found for guild {}", guildId);
                event.getHook().editOriginal("ℹ️ No join counts found for any users in this server.").queue();
                return;
            }

            buildCurrentCountResponse(event, joinCounts);

        } catch (Exception e) {
            log.error("Error in handleCurrentCount", e);
            event.getHook().editOriginal("❌ An error occurred while retrieving current join counts: " + e.getMessage()).queue();
        }
    }

    private void buildCurrentCountResponse(SlashCommandInteractionEvent event, java.util.LinkedHashMap<Long, Integer> joinCounts) {
        StringBuilder responseMessage = new StringBuilder("📊 Current voice join counts for all users in this server:\n");

        // Counter for tracking async operations
        final int[] pendingUsers = {0};
        final boolean[] responseSent = {false};

        // First pass: count how many async operations we'll need
        int asyncOperationsNeeded = 0;
        for (var entry : joinCounts.entrySet()) {
            long userId = entry.getKey();
            var member = event.getGuild().getMemberById(userId);
            if (member == null) {
                asyncOperationsNeeded++;
            }
        }

        pendingUsers[0] = asyncOperationsNeeded;
        log.debug("Building response for {} users, {} async operations needed", joinCounts.size(), asyncOperationsNeeded);

        // Process each user
        for (var entry : joinCounts.entrySet()) {
            long userId = entry.getKey();
            int count = entry.getValue();

            // First try to get member (for current server members)
            var member = event.getGuild().getMemberById(userId);
            if (member != null) {
                responseMessage.append(String.format("• %s: %d joins\n", member.getEffectiveName(), count));
                log.debug("Added current member: {} with {} joins", member.getEffectiveName(), count);
            } else {
                // Add placeholder for users not currently in server
                String placeholder = String.format("• User ID %d: %d joins\n", userId, count);
                responseMessage.append(placeholder);

                // Try to retrieve user from Discord API
                event.getJDA().retrieveUserById(userId).queue(
                        user -> {
                            log.debug("Successfully retrieved user: {} for ID {}", user.getName(), userId);
                            synchronized (responseMessage) {
                                if (!responseSent[0]) {
                                    // Replace placeholder with actual username
                                    String userLine = String.format("• %s: %d joins\n", user.getName(), count);
                                    String content = responseMessage.toString();
                                    responseMessage.setLength(0);
                                    responseMessage.append(content.replace(placeholder, userLine));

                                    pendingUsers[0]--;
                                    log.debug("Async user retrieved, {} operations remaining", pendingUsers[0]);

                                    // Send response if all async operations are done
                                    if (pendingUsers[0] == 0) {
                                        responseSent[0] = true;
                                        log.debug("All async operations complete, sending response");
                                        event.getHook().editOriginal(responseMessage.toString()).queue();
                                    }
                                }
                            }
                        },
                        failure -> {
                            log.warn("Failed to retrieve user info for {}: {}", userId, failure.getMessage());
                            synchronized (responseMessage) {
                                if (!responseSent[0]) {
                                    pendingUsers[0]--;
                                    log.debug("Async user retrieval failed, {} operations remaining", pendingUsers[0]);

                                    // Send response if all async operations are done (even failed ones)
                                    if (pendingUsers[0] == 0) {
                                        responseSent[0] = true;
                                        log.debug("All async operations complete (with failures), sending response");
                                        event.getHook().editOriginal(responseMessage.toString()).queue();
                                    }
                                }
                            }
                        }
                );
            }
        }

        // If no async operations are needed, send response immediately
        if (pendingUsers[0] == 0 && !responseSent[0]) {
            responseSent[0] = true;
            log.debug("No async operations needed, sending response immediately");
            event.getHook().editOriginal(responseMessage.toString()).queue();
        }
    }
}
