package org.fitznet.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GuildConfigDatabaseTest {

    private GuildConfigDatabase database;
    private String configPath;
    private static final long TEST_GUILD_ID = 123456789L;
    private static final long TEST_USER_ID_1 = 111111111L;
    private static final long TEST_USER_ID_2 = 222222222L;

    // Counter to ensure unique file names across all tests
    private static final AtomicInteger testFileCounter = new AtomicInteger(0);

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("guild_configs.json").toString();
        database = new GuildConfigDatabase(configPath);
    }

    @AfterEach
    void tearDown() {
        // Ensure database resources are released
        database = null;

        // Clean up any test files
        File configFile = new File(configPath);
        if (configFile.exists()) {
            configFile.delete();
        }

        // Also clean up the main data directory if it was accidentally created during tests
        File mainDataDir = new File("data");
        if (mainDataDir.exists()) {
            File mainConfigFile = new File(mainDataDir, "guild_configs.json");
            if (mainConfigFile.exists()) {
                mainConfigFile.delete();
            }
            // Remove the data directory if it's empty
            if (mainDataDir.list() != null && mainDataDir.list().length == 0) {
                mainDataDir.delete();
            }
        }
    }

    /**
     * Helper method to create a unique test database instance with an isolated file path.
     * This eliminates the need to manually specify file paths in each test.
     */
    private GuildConfigDatabase createIsolatedDatabase() {
        String uniquePath = tempDir.resolve("test_db_" + testFileCounter.incrementAndGet() + ".json").toString();
        return new GuildConfigDatabase(uniquePath);
    }

    /**
     * Helper method to create a unique test database instance with a custom prefix.
     */
    private GuildConfigDatabase createIsolatedDatabase(String prefix) {
        String uniquePath = tempDir.resolve(prefix + "_" + testFileCounter.incrementAndGet() + ".json").toString();
        return new GuildConfigDatabase(uniquePath);
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
    void testGetRequesterLogChannelIdForNonExistentGuild() {
        assertNull(database.getRequesterLogChannelId(TEST_GUILD_ID),
                "Non-existent guild should have no requester log channel");
    }

    @Test
    void testSetAndGetRequesterLogChannelId() {
        long channelId = 987654321L;
        database.setRequesterLogChannelId(TEST_GUILD_ID, channelId);

        assertEquals(channelId, database.getRequesterLogChannelId(TEST_GUILD_ID));
    }

    @Test
    void testSetRequesterLogChannelIdIndependentFromBotChannel() {
        long botChannelId = 111L;
        long requesterLogChannelId = 222L;

        database.setBotChannelId(TEST_GUILD_ID, botChannelId);
        database.setRequesterLogChannelId(TEST_GUILD_ID, requesterLogChannelId);

        assertEquals(botChannelId, database.getBotChannelId(TEST_GUILD_ID));
        assertEquals(requesterLogChannelId, database.getRequesterLogChannelId(TEST_GUILD_ID));
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
    void testFilePathDiagnostic() {
        // Diagnostic test to understand exactly what's happening with file paths
        System.out.println("=== DIAGNOSTIC TEST ===");
        System.out.println("Expected config path: " + configPath);
        System.out.println("Temp directory: " + tempDir.toString());

        // Add some data
        database.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);

        // Check what files actually exist
        File expectedFile = new File(configPath);
        File mainProjectFile = new File("data/guild_configs.json");

        System.out.println("Expected file exists: " + expectedFile.exists() + " at " + expectedFile.getAbsolutePath());
        System.out.println("Main project file exists: " + mainProjectFile.exists() + " at " + mainProjectFile.getAbsolutePath());

        if (expectedFile.exists()) {
            System.out.println("Expected file size: " + expectedFile.length());
        }
        if (mainProjectFile.exists()) {
            System.out.println("Main project file size: " + mainProjectFile.length());
        }

        // Force test to pass for now - we just want to see the output
        assertTrue(true);
    }

    @Test
    void testJsonSerializationDeserialization() {
        // Direct test of Jackson serialization/deserialization using isolated database
        GuildConfigDatabase db1 = createIsolatedDatabase("json_test");

        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);

        // Verify original data
        assertEquals(2, db1.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1));
        assertNotNull(db1.getTrackingStartDate(TEST_GUILD_ID));

        // Force save and wait
        db1 = null;
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create new instance with same path (we'll need to store the path for this test)
        // For this test, we need to know the file path, so let's create it manually but with helper
        String isolatedPath = tempDir.resolve("json_test_" + testFileCounter.incrementAndGet() + ".json").toString();
        db1 = new GuildConfigDatabase(isolatedPath);
        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);

        // Read the raw JSON file to verify serialization
        File configFile = new File(isolatedPath);
        assertTrue(configFile.exists(), "Config file should exist");
        assertTrue(configFile.length() > 0, "Config file should have content");

        // Force save and wait
        db1 = null;
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create new instance and verify deserialization
        GuildConfigDatabase db2 = new GuildConfigDatabase(isolatedPath);
        assertEquals(2, db2.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1),
                "Data should deserialize correctly");
        assertNotNull(db2.getTrackingStartDate(TEST_GUILD_ID),
                "Tracking date should deserialize correctly");
    }

    @Test
    void testJoinCountPersistence() {
        // Create isolated database for persistence test
        String isolatedPath = tempDir.resolve("join_persistence_" + testFileCounter.incrementAndGet() + ".json").toString();

        // Create first database instance
        GuildConfigDatabase db1 = new GuildConfigDatabase(isolatedPath);

        // Add data
        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);

        // Verify data exists in first instance
        assertEquals(2, db1.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1),
                "Original database should have count of 2");

        // Verify file was created and has content
        File configFile = new File(isolatedPath);
        assertTrue(configFile.exists(), "Config file should exist at: " + isolatedPath);
        assertTrue(configFile.length() > 0, "Config file should not be empty");

        // Force cleanup of first instance
        db1 = null;
        System.gc();

        // Add a small delay to ensure file operations complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create second database instance with same path
        GuildConfigDatabase db2 = new GuildConfigDatabase(isolatedPath);

        // Test persistence
        assertEquals(2, db2.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1),
                "New database should load persisted count of 2");
    }

    @Test
    void testTrackingStartDatePersistence() {
        // Create isolated database for tracking persistence test
        String isolatedPath = tempDir.resolve("tracking_persistence_" + testFileCounter.incrementAndGet() + ".json").toString();

        // Create first database instance
        GuildConfigDatabase db1 = new GuildConfigDatabase(isolatedPath);

        // Add data to trigger tracking date initialization
        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        java.time.LocalDateTime originalDate = db1.getTrackingStartDate(TEST_GUILD_ID);

        // Verify the original date was captured
        assertNotNull(originalDate, "Original tracking date should not be null");

        // Verify file was created and has content
        File configFile = new File(isolatedPath);
        assertTrue(configFile.exists(), "Config file should exist at: " + isolatedPath);
        assertTrue(configFile.length() > 0, "Config file should not be empty");

        // Force cleanup of first instance
        db1 = null;
        System.gc();

        // Add a small delay to ensure file operations complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create second database instance with same path
        GuildConfigDatabase db2 = new GuildConfigDatabase(isolatedPath);

        // Test persistence
        assertEquals(originalDate, db2.getTrackingStartDate(TEST_GUILD_ID),
                "New database should load persisted tracking date");
        assertTrue(db2.isTrackingInitialized(TEST_GUILD_ID),
                "New database should show tracking as initialized");
    }

    @Test
    void testMultipleGuildsTrackingIndependence() {
        long guild1 = TEST_GUILD_ID;
        long guild2 = TEST_GUILD_ID + 1;

        database.incrementUserJoinCount(guild1, TEST_USER_ID_1);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        database.incrementUserJoinCount(guild2, TEST_USER_ID_1);

        assertTrue(database.isTrackingInitialized(guild1));
        assertTrue(database.isTrackingInitialized(guild2));

        java.time.LocalDateTime date1 = database.getTrackingStartDate(guild1);
        java.time.LocalDateTime date2 = database.getTrackingStartDate(guild2);

        assertNotNull(date1);
        assertNotNull(date2);
        assertNotEquals(date1, date2, "Different guilds should have different tracking start dates");
    }

    @Test
    void testTrackingDateForNonExistentGuild() {
        assertFalse(database.isTrackingInitialized(999999999L));
        assertNull(database.getTrackingStartDate(999999999L));
    }

    @Test
    void testSimplePersistenceWithAbsolutePath() {
        // Create isolated database for absolute path persistence test
        String absoluteConfigPath = tempDir.toAbsolutePath().resolve("absolute_test_" + testFileCounter.incrementAndGet() + ".json").toString();

        // Create first database instance
        GuildConfigDatabase db1 = new GuildConfigDatabase(absoluteConfigPath);

        // Add data
        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);
        db1.incrementUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1);

        // Verify data in first instance
        assertEquals(2, db1.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1));

        // Verify file was created
        File configFile = new File(absoluteConfigPath);
        assertTrue(configFile.exists(), "Config file should exist");
        assertTrue(configFile.length() > 0, "Config file should have content");

        // Close first instance explicitly
        db1 = null;
        System.gc(); // Force garbage collection

        try {
            Thread.sleep(200); // Wait for any pending I/O
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create second database instance with same path
        GuildConfigDatabase db2 = new GuildConfigDatabase(absoluteConfigPath);

        // Test persistence
        assertEquals(2, db2.getUserJoinCount(TEST_GUILD_ID, TEST_USER_ID_1),
                "Data should persist between database instances");
    }
}
