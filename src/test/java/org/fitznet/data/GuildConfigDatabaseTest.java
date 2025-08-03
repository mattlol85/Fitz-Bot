package org.fitznet.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GuildConfigDatabaseTest {

    private GuildConfigDatabase database;
    private static final long TEST_GUILD_ID = 123456789L;
    private static final long TEST_USER_ID_1 = 111111111L;
    private static final long TEST_USER_ID_2 = 222222222L;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String configPath = tempDir.resolve("guild_configs.json").toString();
        database = new GuildConfigDatabase(configPath);
    }

    @Test
    void testIncrementUserJoinCount() {
        long count1 = database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        assertEquals(1, count1, "First join should return count of 1");

        long count2 = database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        assertEquals(2, count2, "Second join should return count of 2");

        long retrievedCount = database.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        assertEquals(2, retrievedCount, "Retrieved count should match incremented count");
    }

    @Test
    void testGetUserJoinCountForNonExistentUser() {
        long count = database.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        assertEquals(0, count, "Non-existent user should have count of 0");
    }

    @Test
    void testResetAllJoinCounts() {
        database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_2);

        assertEquals(2, database.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1));
        assertEquals(1, database.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_2));

        int resetCount = database.resetAllJoinCounts(TEST_GUILD_ID);
        assertEquals(2, resetCount, "Should report 2 users were reset");

        assertEquals(0, database.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1));
        assertEquals(0, database.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_2));
    }

    @Test
    void testResetAllJoinCountsForNonExistentGuild() {
        int resetCount = database.resetAllJoinCounts(999999999L);
        assertEquals(0, resetCount, "Resetting non-existent guild should return 0");
    }

    @Test
    void testResetUserJoinCount() {
        database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_2);

        boolean reset = database.resetUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        assertTrue(reset, "Should return true when user had a count to reset");

        assertEquals(0, database.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1));
        assertEquals(1, database.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_2));
    }

    @Test
    void testResetUserJoinCountForNonExistentUser() {
        boolean reset = database.resetUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        assertFalse(reset, "Should return false when user had no count to reset");
    }

    @Test
    void testMultipleGuildsIndependence() {
        long guild1 = TEST_GUILD_ID;
        long guild2 = TEST_GUILD_ID + 1;

        database.incrementUserJoinCount(guild1, TEST_USER_ID_1);
        database.incrementUserJoinCount(guild2, TEST_USER_ID_1);

        database.resetAllJoinCounts(guild1);

        assertEquals(0, database.getUserJoinCount(guild1, TEST_USER_ID_1));
        assertEquals(1, database.getUserJoinCount(guild2, TEST_USER_ID_1));
    }

    @Test
    void testJoinCountPersistence() {
        database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);

        String configPath = tempDir.resolve("guild_configs.json").toString();
        GuildConfigDatabase newDatabase = new GuildConfigDatabase(configPath);

        assertEquals(2, newDatabase.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1));
    }
}
