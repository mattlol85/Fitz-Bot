package org.fitznet.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fitznet.util.Constants;

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
}
