package org.fitznet.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fitznet.util.Constants;

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
     * Gets milestones or returns default values if not configured.
     *
     * @return milestone array or default values
     */
    @JsonIgnore
    public int[] getMilestonesOrDefault() {
        return milestones != null ? milestones : Constants.DEFAULT_MILESTONES;
    }

}
