package org.fitznet.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fitznet.util.Constants;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * POJO representing guild-specific configuration settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuildConfig {

    /**
     * Discord channel ID where bot milestone messages should be sent.
     */
    private Long botChannelId;

    /**
     * Discord role ID permitted to use the /whitelist command.
     * If null, only administrators may add players to the Minecraft whitelist.
     */
    private Long whitelistRoleId;

    /**
     * Discord channel ID where requester log messages should be sent when a user
     * successfully queues a movie or TV show via /joenet download.
     */
    private Long requesterLogChannelId;

    /**
     * Custom milestone thresholds for this guild.
     * If null, default milestones will be used.
     */
    private int[] milestones;

    /**
     * Map of user ID to their voice join count for this guild.
     */
    @Builder.Default
    private Map<Long, Long> userJoinCounts = new ConcurrentHashMap<>();

    /**
     * The date and time when this guild started tracking voice channel joins.
     * Set when the first user joins a voice channel or when tracking is manually initialized.
     * -- GETTER --
     *  Gets the tracking start date or returns null if tracking hasn't started.
     *

     */
    private LocalDateTime trackingStartDate;

    /**
     * Custom setter for Jackson deserialization to ensure userJoinCounts is never null.
     */
    @JsonSetter("userJoinCounts")
    public void setUserJoinCounts(Map<Long, Long> userJoinCounts) {
        this.userJoinCounts = userJoinCounts != null ? new ConcurrentHashMap<>(userJoinCounts) : new ConcurrentHashMap<>();
    }

    /**
     * Getter that ensures userJoinCounts is never null.
     */
    public Map<Long, Long> getUserJoinCounts() {
        if (userJoinCounts == null) {
            userJoinCounts = new ConcurrentHashMap<>();
        }
        return userJoinCounts;
    }

    /**
     * Gets milestones or returns default values if not configured.
     *
     * @return milestone array or default values
     */
    @JsonIgnore
    public int[] getMilestonesOrDefault() {
        return milestones != null ? milestones : Constants.DEFAULT_MILESTONES;
    }

    /**
     * Gets the join count for a specific user.
     *
     * @param userId the Discord user ID
     * @return the join count, or 0 if user not found
     */
    public long getUserJoinCount(long userId) {
        return userJoinCounts.getOrDefault(userId, 0L);
    }

    /**
     * Increments the join count for a specific user.
     *
     * @param userId the Discord user ID
     * @return the new join count after incrementing
     */
    public long incrementUserJoinCount(long userId) {
        return userJoinCounts.compute(userId, (key, oldCount) ->
                oldCount == null ? 1L : oldCount + 1L);
    }

    /**
     * Resets all join counts for this guild.
     */
    public void resetAllJoinCounts() {
        userJoinCounts.clear();
    }

    /**
     * Resets the join count for a specific user.
     *
     * @param userId the Discord user ID
     */
    public void resetUserJoinCount(long userId) {
        userJoinCounts.remove(userId);
    }

    /**
     * Initializes tracking start date if not already set.
     * This should be called when the first user action is tracked.
     */
    public void initializeTrackingIfNeeded() {
        if (trackingStartDate == null) {
            trackingStartDate = LocalDateTime.now();
        }
    }

    /**
     * Checks if tracking has been initialized for this guild.
     *
     * @return true if tracking has started, false otherwise
     */
    @JsonIgnore
    public boolean isTrackingInitialized() {
        return trackingStartDate != null;
    }
}
