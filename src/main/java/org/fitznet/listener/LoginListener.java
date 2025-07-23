package org.fitznet.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.fitznet.data.VoiceJoinDatabase;
import org.fitznet.util.EmbedUtil;
import org.jetbrains.annotations.NotNull;

import static org.fitznet.util.Constants.BOT_MESSAGE_CHANNEL_ID;

/**
 * Discord event listener that tracks voice channel joins and sends milestone congratulations.
 * <p>
 * This listener monitors guild voice update events to detect when users join voice channels
 * from not being in any voice channel (pure joins, not moves between channels).
 * When a user reaches certain milestones (1, 100, 500, 1000, 2000, 5000 joins),
 * a congratulatory message is sent to the designated bot channel.
 * </p>
 */
@Slf4j
public class LoginListener extends ListenerAdapter {
    private final VoiceJoinDatabase voiceDatabase;
    private final int[] loginMilestones = {1, 100, 500, 1000, 2000, 5000};
    private final JDA jda;

    /**
     * Constructs a new LoginListener with the specified JDA instance.
     * Initializes the voice join database for persistent storage of user statistics.
     *
     * @param jda the JDA instance used for Discord API interactions
     */
    public LoginListener(JDA jda) {
        this.jda = jda;
        this.voiceDatabase = new VoiceJoinDatabase();
    }

    /**
     * Handles guild voice update events from Discord.
     * Only processes events where a user joins a voice channel from not being in one.
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
     * A join is considered when the user was not in any voice channel before (channelLeft is null)
     * and is now in a voice channel (channelJoined is not null).
     *
     * @param event the guild voice update event to analyze
     * @return true if the user is joining a voice channel from not being in one, false otherwise
     */
    private boolean isUserJoiningVoiceChannel(GuildVoiceUpdateEvent event) {
        return event.getChannelLeft() == null && event.getChannelJoined() != null;
    }

    /**
     * Processes a voice channel join event by updating user statistics and checking for milestones.
     * Increments the user's voice join count in the database, logs the event,
     * and checks if the user has reached any milestone thresholds.
     *
     * @param event the guild voice update event representing a voice channel join
     */
    private void handleVoiceChannelJoin(GuildVoiceUpdateEvent event) {
        Member user = event.getMember();
        long userId = user.getIdLong();

        long newCount = voiceDatabase.incrementVoiceJoinCount(userId);

        logVoiceJoin(user, event.getGuild().getName(), newCount);
        checkForMilestone(event.getMember(), newCount);

        log.debug("Current database stats: {}", voiceDatabase.getAllCounts());
    }

    /**
     * Logs information about a user's voice channel join event.
     *
     * @param user the Discord member who joined the voice channel
     * @param guildName the name of the guild where the join occurred
     * @param count the total number of times this user has joined voice channels
     */
    private void logVoiceJoin(Member user, String guildName, long count) {
        log.info("User {} has joined a voice channel in {} for the {} time",
                user.getEffectiveName(),
                guildName,
                count);
    }

    /**
     * Checks if a user has reached any milestone and triggers congratulatory message if so.
     * Milestones are defined as specific join counts (1, 100, 500, 1000, 2000, 5000).
     * Only sends one milestone message per join event, even if multiple milestones are reached.
     *
     * @param member the Discord member to check for milestones
     * @param count the current total join count for the user
     */
    private void checkForMilestone(Member member, long count) {
        for (int milestone : loginMilestones) {
            if (count == milestone) {
                sendMilestoneMessage(member, milestone);
                break;
            }
        }
    }

    /**
     * Sends a milestone congratulations message to the designated bot channel.
     * Creates an embed message using the EmbedUtil and queues it for delivery.
     * Handles cases where the bot channel is not found or message sending fails.
     *
     * @param member the Discord member who reached the milestone
     * @param milestone the milestone number that was reached
     */
    private void sendMilestoneMessage(Member member, int milestone) {
        TextChannel botChannel = getBotChannel();
        if (botChannel == null) {
            log.error("Bot channel not found! Cannot send milestone message for {} at {} joins",
                    member.getEffectiveName(), milestone);
            return;
        }

        try {
            MessageEmbed embed = EmbedUtil.createMilestoneEmbed(member, milestone);
            botChannel.sendMessageEmbeds(embed)
                    .queue(
                            message -> log.info("Milestone message sent for {} reaching {} joins. Message: {}",
                                    member.getEffectiveName(), milestone, message.toString()),
                            error -> log.error("Failed to send milestone message for {} at {} joins",
                                    member.getEffectiveName(), milestone, error)
                    );
        } catch (Exception e) {
            log.error("Error creating milestone embed for {}", member.getEffectiveName(), e);
        }
    }

    /**
     * Retrieves the designated bot message channel from Discord.
     * Uses the channel ID defined in Constants to find the appropriate text channel.
     *
     * @return the TextChannel where bot messages should be sent, or null if not found
     */
    private TextChannel getBotChannel() {
        return jda.getTextChannelById(BOT_MESSAGE_CHANNEL_ID);
    }
}
