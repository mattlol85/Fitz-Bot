package org.fitznet.listener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.fitznet.data.GuildConfigDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.CONCURRENT)
@Tag("unit")
@Tag("fast")
class LoginListenerTest {

    @Mock private JDA mockJda;
    @Mock private TextChannel mockTextChannel;
    @Mock private MessageCreateAction mockMessageAction;
    @Mock private Guild mockGuild;
    @Mock private Member mockMember;
    @Mock private User mockUser;
    @Mock private AudioChannelUnion mockAudioChannel;
    @Mock private GuildVoiceUpdateEvent mockEvent;
    @Mock private GuildConfigDatabase mockConfigDatabase;

    private LoginListener listener;
    private static final String TEST_GUILD_ID = "123456789";
    private static final long TEST_GUILD_ID_LONG = 123456789L;
    private static final long TEST_USER_ID = 987654321L;
    private static final long TEST_CHANNEL_ID = 555666777L;

    @BeforeEach
    void setUp() throws Exception {
        listener = new LoginListener();

        // Replace the configDatabase with mock for testing
        setPrivateField(listener, mockConfigDatabase);

        // Setup basic mocks
        lenient().when(mockGuild.getName()).thenReturn("Test Guild");
        lenient().when(mockGuild.getId()).thenReturn(TEST_GUILD_ID);
        lenient().when(mockGuild.getIdLong()).thenReturn(TEST_GUILD_ID_LONG);
        lenient().when(mockMember.getGuild()).thenReturn(mockGuild);
        lenient().when(mockMember.getUser()).thenReturn(mockUser);
        lenient().when(mockMember.getIdLong()).thenReturn(TEST_USER_ID);
        lenient().when(mockMember.getEffectiveName()).thenReturn("TestUser");
        lenient().when(mockUser.getIdLong()).thenReturn(TEST_USER_ID);
        lenient().when(mockEvent.getGuild()).thenReturn(mockGuild);
        lenient().when(mockEvent.getMember()).thenReturn(mockMember);
        lenient().when(mockGuild.getTextChannelById(TEST_CHANNEL_ID)).thenReturn(mockTextChannel);
        lenient().when(mockTextChannel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(mockMessageAction);
        lenient().when(mockTextChannel.getName()).thenReturn("bot-channel");

        // Setup config database to return a bot channel
        lenient().when(mockConfigDatabase.getBotChannelId(TEST_GUILD_ID_LONG)).thenReturn(TEST_CHANNEL_ID);
    }

    private void setPrivateField(Object obj, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField("configDatabase");
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Test
    @Tag("voice-counting")
    void testVoiceJoinIncrementsCount() {
        // Setup
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockAudioChannel);
        when(mockConfigDatabase.incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID)).thenReturn(1L);

        // Execute
        listener.onGuildVoiceUpdate(mockEvent);

        // Verify the database method was called
        verify(mockConfigDatabase).incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID);
    }

    @Test
    @Tag("voice-counting")
    void testGetVoiceJoinCount() {
        when(mockConfigDatabase.getUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID)).thenReturn(5L);

        long count = listener.getVoiceJoinCount(TEST_GUILD_ID, TEST_USER_ID);
        assertEquals(5L, count);
        verify(mockConfigDatabase).getUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID);
    }

    @Test
    @Tag("reset-functionality")
    void testResetAllJoinCounts() {
        when(mockConfigDatabase.resetAllJoinCounts(TEST_GUILD_ID_LONG)).thenReturn(3);

        int resetCount = listener.resetAllJoinCounts(TEST_GUILD_ID_LONG);
        assertEquals(3, resetCount);
        verify(mockConfigDatabase).resetAllJoinCounts(TEST_GUILD_ID_LONG);
    }

    @Test
    @Tag("reset-functionality")
    void testResetUserJoinCount() {
        when(mockConfigDatabase.resetUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID)).thenReturn(true);

        boolean result = listener.resetUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID);
        assertTrue(result);
        verify(mockConfigDatabase).resetUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID);
    }

    @Test
    @Tag("voice-counting")
    void testMultipleJoinsIncrementCorrectly() {
        // Setup
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockAudioChannel);
        when(mockConfigDatabase.incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID))
                .thenReturn(1L, 2L, 3L);

        // Execute multiple joins
        listener.onGuildVoiceUpdate(mockEvent);
        listener.onGuildVoiceUpdate(mockEvent);
        listener.onGuildVoiceUpdate(mockEvent);

        // Verify database was called 3 times
        verify(mockConfigDatabase, times(3)).incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID);
    }

    @Test
    @Tag("milestone")
    @Tag("messaging")
    void testMilestoneMessageSent() {
        // Setup for milestone 1
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockAudioChannel);
        when(mockConfigDatabase.incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID)).thenReturn(1L);

        // Execute
        listener.onGuildVoiceUpdate(mockEvent);

        // Verify milestone message was sent
        verify(mockTextChannel).sendMessageEmbeds(any(MessageEmbed.class));
        verify(mockConfigDatabase).getBotChannelId(TEST_GUILD_ID_LONG);
    }

    @Test
    @Tag("milestone")
    @Tag("config")
    void testNoMilestoneMessageWhenNoBotChannelConfigured() {
        // Setup - no bot channel configured
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockAudioChannel);
        when(mockConfigDatabase.getBotChannelId(TEST_GUILD_ID_LONG)).thenReturn(null);
        when(mockConfigDatabase.incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID)).thenReturn(1L);

        // Execute
        listener.onGuildVoiceUpdate(mockEvent);

        // Verify no milestone message was sent
        verify(mockTextChannel, never()).sendMessageEmbeds(any(MessageEmbed.class));
        // Verify count still increments
        verify(mockConfigDatabase).incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID);
    }

    @Test
    @Tag("voice-events")
    void testNoActionOnChannelMove() {
        // Setup - user moving between channels (not joining or leaving)
        when(mockEvent.getChannelLeft()).thenReturn(mockAudioChannel);
        when(mockEvent.getChannelJoined()).thenReturn(mockAudioChannel);

        // Execute
        listener.onGuildVoiceUpdate(mockEvent);

        // Verify - database method should not be called
        verify(mockConfigDatabase, never()).incrementUserJoinCount(anyLong(), anyLong());
    }

    @Test
    @Tag("voice-events")
    void testNoActionOnChannelLeave() {
        // Setup - user leaving voice channel
        when(mockEvent.getChannelLeft()).thenReturn(mockAudioChannel);
        when(mockEvent.getChannelJoined()).thenReturn(null);

        // Execute
        listener.onGuildVoiceUpdate(mockEvent);

        // Verify - database method should not be called
        verify(mockConfigDatabase, never()).incrementUserJoinCount(anyLong(), anyLong());
    }

    @Test
    @Tag("multi-guild")
    void testDifferentGuildsHaveSeparateCounts() {
        // Setup different guild
        Guild mockGuild2 = mock(Guild.class);
        when(mockGuild2.getId()).thenReturn("987654321");
        when(mockGuild2.getIdLong()).thenReturn(987654321L);
        when(mockGuild2.getName()).thenReturn("Test Guild 2");

        Member mockMember2 = mock(Member.class);
        when(mockMember2.getGuild()).thenReturn(mockGuild2);
        when(mockMember2.getIdLong()).thenReturn(TEST_USER_ID);
        when(mockMember2.getEffectiveName()).thenReturn("TestUser");

        GuildVoiceUpdateEvent mockEvent2 = mock(GuildVoiceUpdateEvent.class);
        when(mockEvent2.getGuild()).thenReturn(mockGuild2);
        when(mockEvent2.getMember()).thenReturn(mockMember2);
        when(mockEvent2.getChannelLeft()).thenReturn(null);
        when(mockEvent2.getChannelJoined()).thenReturn(mockAudioChannel);

        // Setup original event
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockAudioChannel);

        // Mock return values
        when(mockConfigDatabase.incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID))
                .thenReturn(1L, 2L);
        when(mockConfigDatabase.incrementUserJoinCount(987654321L, TEST_USER_ID))
                .thenReturn(1L);

        // Execute joins in different guilds
        listener.onGuildVoiceUpdate(mockEvent);  // Guild 1
        listener.onGuildVoiceUpdate(mockEvent2); // Guild 2
        listener.onGuildVoiceUpdate(mockEvent);  // Guild 1 again

        // Verify separate database calls were made
        verify(mockConfigDatabase, times(2)).incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID);
        verify(mockConfigDatabase, times(1)).incrementUserJoinCount(987654321L, TEST_USER_ID);
    }

    @Test
    @Tag("milestone")
    @Tag("slow")
    void testMilestoneOnlyAtSpecificCounts() {
        // Setup
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockAudioChannel);

        // Mock incremental return values for 100 calls
        Long[] returnValues = new Long[100];
        for (int i = 0; i < 100; i++) {
            returnValues[i] = (long) (i + 1);
        }
        when(mockConfigDatabase.incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID))
                .thenReturn(returnValues[0], returnValues);

        // Execute joins up to milestone 100
        for (int i = 1; i <= 100; i++) {
            listener.onGuildVoiceUpdate(mockEvent);
        }

        // Verify database was called 100 times
        verify(mockConfigDatabase, times(100)).incrementUserJoinCount(TEST_GUILD_ID_LONG, TEST_USER_ID);
    }

    @Test
    @Tag("config")
    void testSetAndGetBotChannel() {
        // Test setting bot channel
        listener.setBotChannel(TEST_GUILD_ID_LONG, TEST_CHANNEL_ID);
        verify(mockConfigDatabase).setBotChannelId(TEST_GUILD_ID_LONG, TEST_CHANNEL_ID);

        // Test getting bot channel
        when(mockConfigDatabase.getBotChannelId(TEST_GUILD_ID_LONG)).thenReturn(TEST_CHANNEL_ID);
        Long result = listener.getBotChannelId(TEST_GUILD_ID_LONG);
        assertEquals(TEST_CHANNEL_ID, result);
    }
}
