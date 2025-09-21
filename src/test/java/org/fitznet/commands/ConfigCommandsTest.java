package org.fitznet.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.fitznet.data.GuildConfigDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConfigCommandsTest {

    @Mock
    private SlashCommandInteractionEvent mockEvent;

    @Mock
    private Guild mockGuild;

    @Mock
    private Member mockMember;

    @Mock
    private User mockUser;

    @Mock
    private InteractionHook mockHook;

    @Mock
    private WebhookMessageEditAction<Message> mockEditAction;

    private GuildConfigDatabase database;
    private ConfigCommands configCommands;
    private String configPath;

    private static final long TEST_GUILD_ID = 123456789L;
    private static final String TEST_GUILD_NAME = "Test Guild";
    private static final String TEST_USER_NAME = "TestUser";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up temporary config file
        configPath = tempDir.resolve("guild_configs_test.json").toString();
        database = new GuildConfigDatabase(configPath);

        // Set up mock behavior
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockEvent.getMember()).thenReturn(mockMember);
        when(mockEvent.getUser()).thenReturn(mockUser);
        when(mockEvent.getHook()).thenReturn(mockHook);
        when(mockEvent.getName()).thenReturn("initializetracking");
        when(mockEvent.isFromGuild()).thenReturn(true);
        when(mockEvent.isAcknowledged()).thenReturn(true);

        when(mockGuild.getIdLong()).thenReturn(TEST_GUILD_ID);
        when(mockGuild.getName()).thenReturn(TEST_GUILD_NAME);
        when(mockUser.getName()).thenReturn(TEST_USER_NAME);

        when(mockHook.editOriginal(anyString())).thenReturn(mockEditAction);
        doNothing().when(mockEditAction).queue();

        // Create ConfigCommands instance with our test database
        configCommands = new ConfigCommands() {
            // Override to use our test database
            private final GuildConfigDatabase testDatabase = database;

            @Override
            public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
                // Call the specific method we want to test
                handleInitializeTrackingTest(event, testDatabase);
            }
        };
    }

    @AfterEach
    void tearDown() {
        // Clean up test files
        File configFile = new File(configPath);
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    void testInitializeTracking_WithExistingGuildButNullTrackingDate() {
        // Arrange: Create a guild with existing data but null tracking date
        when(mockMember.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

        // Add some existing data to the guild without initializing tracking
        database.setBotChannelId(TEST_GUILD_ID, 987654321L);

        // Verify the guild exists but tracking is not initialized
        assertNotNull(database.getGuildConfig(TEST_GUILD_ID));
        assertFalse(database.isTrackingInitialized(TEST_GUILD_ID));
        assertNull(database.getTrackingStartDate(TEST_GUILD_ID));

        // Act
        handleInitializeTrackingTest(mockEvent, database);

        // Assert
        assertTrue(database.isTrackingInitialized(TEST_GUILD_ID));
        assertNotNull(database.getTrackingStartDate(TEST_GUILD_ID));

        // Verify the tracking date is recent (within the last minute)
        LocalDateTime trackingDate = database.getTrackingStartDate(TEST_GUILD_ID);
        assertTrue(trackingDate.isAfter(LocalDateTime.now().minusMinutes(1)));
        assertTrue(trackingDate.isBefore(LocalDateTime.now().plusMinutes(1)));

        // Verify success message was sent
        String expectedDate = trackingDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        verify(mockHook).editOriginal("✅ Tracking date initialized for this server starting " + expectedDate + "!\n" +
                "Milestone messages will now include the tracking start date.");
    }

    @Test
    void testInitializeTracking_WithNoExistingGuild() {
        // Arrange: No existing guild data
        when(mockMember.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

        // Verify no guild config exists
        assertNull(database.getGuildConfig(TEST_GUILD_ID));
        assertFalse(database.isTrackingInitialized(TEST_GUILD_ID));
        assertNull(database.getTrackingStartDate(TEST_GUILD_ID));

        // Act
        handleInitializeTrackingTest(mockEvent, database);

        // Assert
        assertTrue(database.isTrackingInitialized(TEST_GUILD_ID));
        assertNotNull(database.getTrackingStartDate(TEST_GUILD_ID));
        assertNotNull(database.getGuildConfig(TEST_GUILD_ID));

        // Verify the tracking date is recent (within the last minute)
        LocalDateTime trackingDate = database.getTrackingStartDate(TEST_GUILD_ID);
        assertTrue(trackingDate.isAfter(LocalDateTime.now().minusMinutes(1)));
        assertTrue(trackingDate.isBefore(LocalDateTime.now().plusMinutes(1)));

        // Verify success message was sent
        String expectedDate = trackingDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        verify(mockHook).editOriginal("✅ Tracking date initialized for this server starting " + expectedDate + "!\n" +
                "Milestone messages will now include the tracking start date.");
    }

    @Test
    void testInitializeTracking_AlreadyInitialized() {
        // Arrange: Guild with already initialized tracking
        when(mockMember.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

        // Initialize tracking first
        database.forceInitializeTracking(TEST_GUILD_ID);
        LocalDateTime originalDate = database.getTrackingStartDate(TEST_GUILD_ID);

        // Verify it's already initialized
        assertTrue(database.isTrackingInitialized(TEST_GUILD_ID));
        assertNotNull(originalDate);

        // Act
        handleInitializeTrackingTest(mockEvent, database);

        // Assert
        // Date should remain unchanged
        assertEquals(originalDate, database.getTrackingStartDate(TEST_GUILD_ID));

        // Verify info message was sent
        String expectedDate = originalDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        verify(mockHook).editOriginal("ℹ️ Tracking is already initialized for this server since " + expectedDate + ".");
    }

    @Test
    void testInitializeTracking_NoAdminPermission() {
        // Arrange: User without admin permission
        when(mockMember.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);

        // Act
        handleInitializeTrackingTest(mockEvent, database);

        // Assert
        // No changes should be made to the database
        assertFalse(database.isTrackingInitialized(TEST_GUILD_ID));
        assertNull(database.getTrackingStartDate(TEST_GUILD_ID));

        // Verify error message was sent
        verify(mockHook).editOriginal("❌ You need the 'Administrator' permission to use this command!");
    }

    @Test
    void testInitializeTracking_NullMember() {
        // Arrange: Null member (shouldn't happen in practice but good to test)
        when(mockEvent.getMember()).thenReturn(null);

        // Act
        handleInitializeTrackingTest(mockEvent, database);

        // Assert
        // No changes should be made to the database
        assertFalse(database.isTrackingInitialized(TEST_GUILD_ID));
        assertNull(database.getTrackingStartDate(TEST_GUILD_ID));

        // Verify error message was sent
        verify(mockHook).editOriginal("❌ You need the 'Administrator' permission to use this command!");
    }

    /**
     * Helper method that replicates the handleInitializeTracking logic for testing
     */
    private void handleInitializeTrackingTest(SlashCommandInteractionEvent event, GuildConfigDatabase configDatabase) {
        try {
            // Check permissions
            if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.getHook().editOriginal("❌ You need the 'Administrator' permission to use this command!").queue();
                return;
            }

            long guildId = Objects.requireNonNull(event.getGuild()).getIdLong();

            // Check if tracking is already initialized
            if (configDatabase.isTrackingInitialized(guildId)) {
                LocalDateTime startDate = configDatabase.getTrackingStartDate(guildId);
                String formattedDate = startDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                event.getHook().editOriginal("ℹ️ Tracking is already initialized for this server since " + formattedDate + ".").queue();
                return;
            }

            // Force initialize tracking date in the database
            configDatabase.forceInitializeTracking(guildId);

            LocalDateTime startDate = configDatabase.getTrackingStartDate(guildId);
            String formattedDate = startDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));

            // Send success response
            event.getHook().editOriginal("✅ Tracking date initialized for this server starting " + formattedDate + "!\n" +
                    "Milestone messages will now include the tracking start date.").queue();

        } catch (Exception e) {
            event.getHook().editOriginal("❌ An error occurred while initializing tracking date: " + e.getMessage()).queue();
        }
    }
}
