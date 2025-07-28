package org.fitznet.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * POJO representing the complete guild configurations data structure.
 * Maps guild IDs to their respective configurations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuildConfigurations {

    /**
     * Map of guild ID to guild configuration.
     * Key: Discord guild ID (as Long)
     * Value: Guild-specific configuration
     */
    @Builder.Default
    private Map<Long, GuildConfig> guilds = new HashMap<>();

    /**
     * Gets configuration for a specific guild.
     *
     * @param guildId Discord guild ID
     * @return guild configuration or null if not found
     */
    public GuildConfig getGuildConfig(Long guildId) {
        return guilds.get(guildId);
    }

    /**
     * Sets configuration for a specific guild.
     *
     * @param guildId Discord guild ID
     * @param config guild configuration
     */
    public void setGuildConfig(Long guildId, GuildConfig config) {
        guilds.put(guildId, config);
    }

    /**
     * Gets or creates configuration for a guild.
     *
     * @param guildId Discord guild ID
     * @return existing or new guild configuration
     */
    public GuildConfig getOrCreateGuildConfig(Long guildId) {
        return guilds.computeIfAbsent(guildId, k -> new GuildConfig());
    }

    /**
     * Removes configuration for a guild.
     *
     * @param guildId Discord guild ID
     * @return removed configuration or null if not found
     */
    public GuildConfig removeGuildConfig(Long guildId) {
        return guilds.remove(guildId);
    }

    /**
     * Checks if a guild has any configuration.
     *
     * @param guildId Discord guild ID
     * @return true if guild has configuration
     */
    public boolean hasGuildConfig(Long guildId) {
        return guilds.containsKey(guildId);
    }

    /**
     * Gets the number of configured guilds.
     *
     * @return number of guilds with configurations
     */
    public int getGuildCount() {
        return guilds.size();
    }
}
