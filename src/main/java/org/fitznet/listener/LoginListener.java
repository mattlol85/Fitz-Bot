package org.fitznet.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.fitznet.data.GuildConfigDatabase;
import org.fitznet.util.Constants;
import org.fitznet.util.EmbedUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Discord event listener that tracks voice channel joins.
 * Maintains voice join counts in persistent storage and sends milestone congratulations.
 */
@Slf4j
public class LoginListener extends ListenerAdapter {
    private final int[] loginMilestones = Constants.DEFAULT_MILESTONES;
    private final GuildConfigDatabase configDatabase;

    /**
     * Constructs a new LoginListener.
     */
    public LoginListener() {
        this.configDatabase = new GuildConfigDatabase();
    }

    /**
     * Handles guild voice update events from Discord.
     * Only processes voice channel joins.
     *
     * @param event the guild voice update event containing voice channel change information
     */
    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (isUserJoiningVoiceChannel(event)) {
            handleVoiceChannelJoin(event);
        }
    }

    /**
     * Determines if the voice update event represents a user joining a voice channel.
     *
     * @param event the guild voice update event to analyze
     * @return true if the user is joining a voice channel from not being in one
     */
    private boolean isUserJoiningVoiceChannel(GuildVoiceUpdateEvent event) {
        return event.getChannelLeft() == null && event.getChannelJoined() != null;
    }

    /**
     * Processes a voice channel join event by updating user statistics and checking for milestones.
     *
     * @param event the guild voice update event representing a voice channel join
     */
    private void handleVoiceChannelJoin(GuildVoiceUpdateEvent event) {
        Member user = event.getMember();
        Guild guild = event.getGuild();
        long guildId = guild.getIdLong();
        long userId = user.getIdLong();

        long newCount = configDatabase.incrementUserJoinCount(guildId, userId);
        logVoiceJoin(user, guild.getName(), newCount);
        checkForMilestone(guild, user, newCount);
    }

    /**
     * Gets the current voice join count for a user in a specific guild.
     *
     * @param guildId the Discord guild ID
     * @param userId the Discord user ID
     * @return current join count, or 0 if user not found
     */
    public long getVoiceJoinCount(String guildId, long userId) {
        return configDatabase.getUserJoinCount(Long.parseLong(guildId), userId);
    }

    /**
     * Gets the current voice join count for a user in a specific guild.
     *
     * @param guildId the Discord guild ID as long
     * @param userId the Discord user ID
     * @return current join count, or 0 if user not found
     */
    public long getVoiceJoinCount(long guildId, long userId) {
        return configDatabase.getUserJoinCount(guildId, userId);
    }

    /**
     * Resets all join counts for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @return the number of users whose counts were reset
     */
    public int resetAllJoinCounts(long guildId) {
        return configDatabase.resetAllJoinCounts(guildId);
    }

    /**
     * Resets the join count for a specific user in a guild.
     *
     * @param guildId the Discord guild ID
     * @param userId the Discord user ID
     * @return true if the user had a count to reset, false otherwise
     */
    public boolean resetUserJoinCount(long guildId, long userId) {
        return configDatabase.resetUserJoinCount(guildId, userId);
    }

    /**
     * Logs information about a user's voice channel join event.
     *
     * @param user the Discord member who joined the voice channel
     * @param guildName the name of the guild where the join occurred
     * @param count the total number of times this user has joined voice channels in this guild
     */
    private void logVoiceJoin(Member user, String guildName, long count) {
        log.info("User {} has joined a voice channel in {} for the {} time",
                user.getEffectiveName(), guildName, count);
    }

    /**
     * Checks if a user has reached any milestone and triggers congratulatory message if so.
     *
     * @param guild the Discord guild where the milestone was reached
     * @param member the Discord member who reached the milestone
     * @param count the current total join count for the user in this guild
     */
    private void checkForMilestone(Guild guild, Member member, long count) {
        for (int milestone : loginMilestones) {
            if (count == milestone) {
                sendMilestoneMessage(guild, member, milestone);
                break;
            }
        }
    }

    /**
     * Sends a milestone congratulations message to the guild's configured bot channel.
     *
     * @param guild the Discord guild where the milestone was reached
     * @param member the Discord member who reached the milestone
     * @param milestone the milestone number that was reached
     */
    private void sendMilestoneMessage(Guild guild, Member member, int milestone) {
        Long botChannelId = configDatabase.getBotChannelId(guild.getIdLong());

        if (botChannelId == null) {
            log.warn("No bot channel configured for guild {}. Use /setbotchannel command to configure it. Skipping milestone message for {} at {} joins",
                    guild.getName(), member.getEffectiveName(), milestone);
            return;
        }

        TextChannel botChannel = guild.getTextChannelById(botChannelId);
        if (botChannel == null) {
            log.warn("Configured bot channel {} not found in guild {}. Channel may have been deleted. Skipping milestone message for {} at {} joins",
                    botChannelId, guild.getName(), member.getEffectiveName(), milestone);
            return;
        }

        try {
            MessageEmbed embed = EmbedUtil.createMilestoneEmbed(member, milestone);
            botChannel.sendMessageEmbeds(embed)
                    .queue(
                            message -> log.info("Milestone message sent for {} reaching {} joins in guild {} (channel: {})",
                                    member.getEffectiveName(), milestone, guild.getName(), botChannel.getName()),
                            error -> log.error("Failed to send milestone message for {} at {} joins in guild {}",
                                    member.getEffectiveName(), milestone, guild.getName(), error)
                    );
        } catch (Exception e) {
            log.error("Error creating milestone embed for {} in guild {}",
                    member.getEffectiveName(), guild.getName(), e);
        }
    }

    /**
     * Sets the bot channel for milestone messages in a guild.
     *
     * @param guildId the Discord guild ID
     * @param channelId the Discord channel ID to use for bot messages
     */
    public void setBotChannel(long guildId, long channelId) {
        configDatabase.setBotChannelId(guildId, channelId);
        log.info("Bot channel set to {} for guild {}", channelId, guildId);
    }

    /**
     * Gets the configured bot channel ID for a guild.
     *
     * @param guildId the Discord guild ID
     * @return the bot channel ID, or null if not configured
     */
    public Long getBotChannelId(long guildId) {
        return configDatabase.getBotChannelId(guildId);
    }
}
