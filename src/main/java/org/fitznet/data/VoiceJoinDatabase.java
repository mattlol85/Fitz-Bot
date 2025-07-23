package org.fitznet.data;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.fitznet.util.Constants.TOTALLY_LEGIT_DATABASE_FILENAME;

@Slf4j
public class VoiceJoinDatabase {
    private final Map<Long, Long> voiceJoinCounts = new ConcurrentHashMap<>();
    private final String databaseFile;

    public VoiceJoinDatabase() {
        this.databaseFile = TOTALLY_LEGIT_DATABASE_FILENAME;
        loadData();
    }

    /**
     * Load voice join counts from JSON file
     */
    private void loadData() {
        try {
            File file = new File(databaseFile);
            if (file.exists() && file.length() > 0) {
                Map<Long, Long> loadedData = JsonUtils.MAPPER.readValue(file, new TypeReference<>() {
                });
                voiceJoinCounts.putAll(loadedData);
                log.info("Loaded {} user voice join counts from database", loadedData.size());
            } else {
                boolean isNewFileCreated = file.createNewFile();
                log.info("Created new voice join database file: {}", isNewFileCreated);
            }
        } catch (IOException e) {
            log.error("Failed to load voice join data from file. Starting with empty database.", e);
        }
    }

    /**
     * Save voice join counts to JSON file
     */
    public void saveData() {
        try {
            JsonUtils.MAPPER.writeValue(new File(databaseFile), voiceJoinCounts);
            log.debug("Voice join counts saved to database successfully");
        } catch (IOException e) {
            log.warn("Failed to save voice join counts to file", e);
        }
    }

    /**
     * Get the current voice join count for a user
     * @param userId Discord user ID
     * @return Current join count, or 0 if user not found
     */
    public long getVoiceJoinCount(long userId) {
        return voiceJoinCounts.getOrDefault(userId, 0L);
    }

    /**
     * Increment voice join count for a user and return the new count
     * @param userId Discord user ID
     * @return New join count after incrementing
     */
    public long incrementVoiceJoinCount(long userId) {
        long newCount = voiceJoinCounts.compute(userId, (key, oldCount) ->
            oldCount == null ? 1L : oldCount + 1L);
        saveData(); // Auto-save after each increment
        return newCount;
    }

    /**
     * Get all voice join counts (read-only copy)
     * @return Map of user IDs to join counts
     */
    public Map<Long, Long> getAllCounts() {
        return new HashMap<>(voiceJoinCounts);
    }

    /**
     * Check if a user exists in the database
     * @param userId Discord user ID
     * @return true if user has at least one voice join recorded
     */
    public boolean userExists(long userId) {
        return voiceJoinCounts.containsKey(userId);
    }
}
