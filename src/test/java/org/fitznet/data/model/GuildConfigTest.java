package org.fitznet.data.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for GuildConfig join count functionality.
 */
class GuildConfigTest {

    private GuildConfig guildConfig;
    private static final long TEST_USER_ID_1 = 111111111L;
    private static final long TEST_USER_ID_2 = 222222222L;

    @BeforeEach
    void setUp() {
        guildConfig = new GuildConfig();
    }

    @Test
    void testGetUserJoinCountForNewUser() {
        long count = guildConfig.getUserJoinCount(TEST_USER_ID_1);
        assertEquals(0, count, "New user should have count of 0");
    }

    @Test
    void testIncrementUserJoinCount() {
        // First increment
        long count1 = guildConfig.incrementUserJoinCount(TEST_USER_ID_1);
        assertEquals(1, count1, "First increment should return 1");

        // Second increment
        long count2 = guildConfig.incrementUserJoinCount(TEST_USER_ID_1);
        assertEquals(2, count2, "Second increment should return 2");

        // Verify count persists
        assertEquals(2, guildConfig.getUserJoinCount(TEST_USER_ID_1));
    }

    @Test
    void testIncrementMultipleUsers() {
        guildConfig.incrementUserJoinCount(TEST_USER_ID_1);
        guildConfig.incrementUserJoinCount(TEST_USER_ID_2);
        guildConfig.incrementUserJoinCount(TEST_USER_ID_1);

        assertEquals(2, guildConfig.getUserJoinCount(TEST_USER_ID_1));
        assertEquals(1, guildConfig.getUserJoinCount(TEST_USER_ID_2));
    }

    @Test
    void testResetAllJoinCounts() {
        // Set up some counts
        guildConfig.incrementUserJoinCount(TEST_USER_ID_1);
        guildConfig.incrementUserJoinCount(TEST_USER_ID_2);
        guildConfig.incrementUserJoinCount(TEST_USER_ID_1);

        // Verify counts exist
        assertEquals(2, guildConfig.getUserJoinCount(TEST_USER_ID_1));
        assertEquals(1, guildConfig.getUserJoinCount(TEST_USER_ID_2));

        // Reset all
        guildConfig.resetAllJoinCounts();

        // Verify all counts are reset
        assertEquals(0, guildConfig.getUserJoinCount(TEST_USER_ID_1));
        assertEquals(0, guildConfig.getUserJoinCount(TEST_USER_ID_2));
        assertTrue(guildConfig.getUserJoinCounts().isEmpty(), "Join counts map should be empty");
    }

    @Test
    void testResetUserJoinCount() {
        // Set up counts
        guildConfig.incrementUserJoinCount(TEST_USER_ID_1);
        guildConfig.incrementUserJoinCount(TEST_USER_ID_2);

        // Reset specific user
        guildConfig.resetUserJoinCount(TEST_USER_ID_1);

        // Verify only specific user was reset
        assertEquals(0, guildConfig.getUserJoinCount(TEST_USER_ID_1));
        assertEquals(1, guildConfig.getUserJoinCount(TEST_USER_ID_2));
    }

    @Test
    void testResetNonExistentUser() {
        // This should not throw an exception
        guildConfig.resetUserJoinCount(TEST_USER_ID_1);
        assertEquals(0, guildConfig.getUserJoinCount(TEST_USER_ID_1));
    }

    @Test
    void testBuilderPattern() {
        GuildConfig config = GuildConfig.builder()
                .botChannelId(123L)
                .milestones(new int[]{5, 10, 25})
                .build();

        assertNotNull(config.getUserJoinCounts(), "Join counts map should be initialized");
        assertEquals(123L, config.getBotChannelId());
        assertArrayEquals(new int[]{5, 10, 25}, config.getMilestones());
    }

    @Test
    void testGetMilestonesOrDefault() {
        // Test default milestones when not set
        int[] defaultMilestones = guildConfig.getMilestonesOrDefault();
        assertNotNull(defaultMilestones, "Should return default milestones");

        // Test custom milestones
        int[] customMilestones = {1, 2, 3};
        guildConfig.setMilestones(customMilestones);
        assertArrayEquals(customMilestones, guildConfig.getMilestonesOrDefault());
    }
}
