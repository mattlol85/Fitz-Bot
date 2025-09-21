package org.fitznet.data;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.data.model.GuildConfig;
import org.fitznet.util.Constants;
import org.fitznet.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database for storing guild-specific configuration settings.
 * Each guild can have its own bot channel and other settings.
 */
@Slf4j
public class GuildConfigDatabase {
    private final Map<Long, GuildConfig> guildConfigs = new ConcurrentHashMap<>();
    private final String configFile;

    public GuildConfigDatabase() {
        this("data/guild_configs.json");
    }

    public GuildConfigDatabase(String configFilePath) {
        this.configFile = configFilePath;
        createDataDirectory();
        loadConfigs();
    }

    /**
     * Creates the data directory if it doesn't exist.
     */
    private void createDataDirectory() {
        File configFileObj = new File(configFile);
        File dir = configFileObj.getParentFile();

        if (dir != null) {
            log.info("Checking data directory at: {}", dir.getAbsolutePath());

            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                log.info("Created data directory: {} at path: {}", created, dir.getAbsolutePath());
            } else {
                log.info("Data directory already exists at: {}", dir.getAbsolutePath());
            }
        }
    }

    /**
     * Loads guild configurations from JSON file.
     */
    private void loadConfigs() {
        try {
            File file = new File(configFile);
            log.info("Loading config from: {}", file.getAbsolutePath());

            if (file.exists() && file.length() > 0) {
                log.debug("Config file size: {} bytes", file.length());
                Map<Long, GuildConfig> loadedConfigs = JsonUtils.MAPPER.readValue(file, new TypeReference<>() {});

                if (loadedConfigs != null) {
                    guildConfigs.putAll(loadedConfigs);
                    log.info("Loaded configurations for {} guilds from {}", loadedConfigs.size(), file.getAbsolutePath());

                    // Debug log the loaded data
                    for (Map.Entry<Long, GuildConfig> entry : loadedConfigs.entrySet()) {
                        GuildConfig config = entry.getValue();
                        if (config.getUserJoinCounts() != null && !config.getUserJoinCounts().isEmpty()) {
                            log.debug("Guild {} has {} user join counts, tracking initialized: {}",
                                    entry.getKey(), config.getUserJoinCounts().size(), config.isTrackingInitialized());
                        }
                    }
                } else {
                    log.warn("Loaded configs is null from file: {}", file.getAbsolutePath());
                }
            } else {
                if (!file.exists()) {
                    // Ensure parent directory exists
                    file.getParentFile().mkdirs();
                    boolean created = file.createNewFile();
                    log.info("Created new guild config file: {} at {}", created, file.getAbsolutePath());
                } else {
                    log.info("Config file exists but is empty: {}", file.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            log.error("Failed to load guild configurations from {}. Starting with empty configs.", configFile, e);
        }
    }

    /**
     * Saves guild configurations to JSON file.
     */
    private void saveConfigs() {
        try {
            JsonUtils.MAPPER.writeValue(new File(configFile), guildConfigs);
            log.debug("Guild configurations saved successfully");
        } catch (IOException e) {
            log.warn("Failed to save guild configurations", e);
        }
    }

    /**
     * Gets the bot channel ID for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @return the bot channel ID, or null if not configured
     */
    public Long getBotChannelId(long guildId) {
        GuildConfig config = guildConfigs.get(guildId);
        return config != null ? config.getBotChannelId() : null;
    }

    /**
     * Sets the bot channel ID for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @param channelId the Discord channel ID
     */
    public void setBotChannelId(long guildId, long channelId) {
        GuildConfig config = guildConfigs.computeIfAbsent(guildId, k -> new GuildConfig());
        config.setBotChannelId(channelId);
        saveConfigs();
        log.info("Set bot channel for guild {} to {}", guildId, channelId);
    }

    /**
     * Gets the milestone thresholds for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @return array of milestone thresholds, or default values if not configured
     */
    public int[] getMilestones(long guildId) {
        GuildConfig config = guildConfigs.get(guildId);
        return config != null ? config.getMilestonesOrDefault() : Constants.DEFAULT_MILESTONES;
    }

    /**
     * Sets custom milestone thresholds for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @param milestones array of milestone thresholds
     */
    public void setMilestones(long guildId, int[] milestones) {
        GuildConfig config = guildConfigs.computeIfAbsent(guildId, k -> new GuildConfig());
        config.setMilestones(milestones);
        saveConfigs();
        log.info("Set custom milestones for guild {}: {}", guildId, milestones);
    }

    /**
     * Checks if a guild has any configuration.
     *
     * @param guildId the Discord guild ID
     * @return true if guild has configuration, false otherwise
     */
    public boolean hasConfig(long guildId) {
        return guildConfigs.containsKey(guildId);
    }

    /**
     * Gets the complete configuration for a guild.
     *
     * @param guildId the Discord guild ID
     * @return guild configuration or null if not found
     */
    public GuildConfig getGuildConfig(long guildId) {
        return guildConfigs.get(guildId);
    }

    /**
     * Removes all configuration for a guild.
     *
     * @param guildId the Discord guild ID
     */
    public void removeGuildConfig(long guildId) {
        guildConfigs.remove(guildId);
        saveConfigs();
        log.info("Removed configuration for guild {}", guildId);
    }

    /**
     * Gets the join count for a specific user in a guild.
     *
     * @param guildId the Discord guild ID
     * @param userId the Discord user ID
     * @return the join count, or 0 if user not found
     */
    public long getUserJoinCount(long guildId, long userId) {
        GuildConfig config = guildConfigs.get(guildId);
        return config != null ? config.getUserJoinCount(userId) : 0L;
    }

    /**
     * Increments the join count for a specific user in a guild.
     *
     * @param guildId the Discord guild ID
     * @param userId the Discord user ID
     * @return the new join count after incrementing
     */
    public long incrementUserJoinCount(long guildId, long userId) {
        GuildConfig config = guildConfigs.computeIfAbsent(guildId, k -> new GuildConfig());

        // Initialize tracking date if this is the first interaction
        config.initializeTrackingIfNeeded();

        long newCount = config.incrementUserJoinCount(userId);
        saveConfigs();
        return newCount;
    }

    /**
     * Resets all join counts for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @return the number of users whose counts were reset
     */
    public int resetAllJoinCounts(long guildId) {
        GuildConfig config = guildConfigs.get(guildId);
        if (config == null) {
            log.info("No configuration found for guild {}, nothing to reset", guildId);
            return 0;
        }

        int userCount = config.getUserJoinCounts().size();
        config.resetAllJoinCounts();
        saveConfigs();
        log.info("Reset join counts for {} users in guild {}", userCount, guildId);
        return userCount;
    }

    /**
     * Resets the join count for a specific user in a guild.
     *
     * @param guildId the Discord guild ID
     * @param userId the Discord user ID
     * @return true if the user had a count to reset, false otherwise
     */
    public boolean resetUserJoinCount(long guildId, long userId) {
        GuildConfig config = guildConfigs.get(guildId);
        if (config == null) {
            log.info("No configuration found for guild {}, user {} has no count to reset", guildId, userId);
            return false;
        }

        boolean hadCount = config.getUserJoinCounts().containsKey(userId);
        config.resetUserJoinCount(userId);
        if (hadCount) {
            saveConfigs();
            log.info("Reset join count for user {} in guild {}", userId, guildId);
        }
        return hadCount;
    }

    /**
     * Gets the tracking start date for a specific guild.
     *
     * @param guildId the Discord guild ID
     * @return the tracking start date, or null if not initialized
     */
    public java.time.LocalDateTime getTrackingStartDate(long guildId) {
        GuildConfig config = guildConfigs.get(guildId);
        return config != null ? config.getTrackingStartDate() : null;
    }

    /**
     * Checks if tracking has been initialized for a guild.
     *
     * @param guildId the Discord guild ID
     * @return true if tracking has started, false otherwise
     */
    public boolean isTrackingInitialized(long guildId) {
        GuildConfig config = guildConfigs.get(guildId);
        return config != null && config.isTrackingInitialized();
    }

    /**
     * Forces initialization of tracking for a guild, even if it already exists.
     * This is useful for existing guilds that were created before tracking dates were implemented.
     *
     * @param guildId the Discord guild ID
     */
    public void forceInitializeTracking(long guildId) {
        GuildConfig config = guildConfigs.computeIfAbsent(guildId, k -> new GuildConfig());
        if (config.getTrackingStartDate() == null) {
            config.initializeTrackingIfNeeded();
            saveConfigs();
            log.info("Initialized tracking date for guild {}", guildId);
        }
    }
}
