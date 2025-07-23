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
import org.fitznet.data.VoiceJoinDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;

import static org.fitznet.util.Constants.BOT_MESSAGE_CHANNEL_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginListenerTest {

    @Mock private JDA mockJda;
    @Mock private TextChannel mockTextChannel;
    @Mock private MessageCreateAction mockMessageAction;
    @Mock private Guild mockGuild;
    @Mock private Member mockMember;
    @Mock private User mockUser;
    @Mock private AudioChannelUnion mockVoiceChannel;
    @Mock private GuildVoiceUpdateEvent mockEvent;
    @Mock private VoiceJoinDatabase mockDatabase;

    private LoginListener listener;

    @BeforeEach
    void setUp() throws Exception {
        // Setup basic mocks that are used across multiple tests
        lenient().when(mockGuild.getName()).thenReturn("Test Guild");
        lenient().when(mockMember.getGuild()).thenReturn(mockGuild);
        lenient().when(mockMember.getUser()).thenReturn(mockUser);
        lenient().when(mockMember.getAsMention()).thenReturn("@TestUser");
        lenient().when(mockMember.getEffectiveName()).thenReturn("TestUser");
        lenient().when(mockMember.getIdLong()).thenReturn(123L);
        lenient().when(mockUser.getEffectiveAvatarUrl()).thenReturn("https://example.com/avatar.jpg");

        lenient().when(mockEvent.getMember()).thenReturn(mockMember);
        lenient().when(mockEvent.getGuild()).thenReturn(mockGuild);

        // Create listener and inject mock database
        listener = new LoginListener(mockJda);
        injectMockDatabase(listener, mockDatabase);
    }

    private void injectMockDatabase(LoginListener listener, VoiceJoinDatabase mockDatabase) throws Exception {
        Field databaseField = LoginListener.class.getDeclaredField("voiceDatabase");
        databaseField.setAccessible(true);
        databaseField.set(listener, mockDatabase);
    }

    @Test
    void shouldHandleVoiceChannelJoin() {
        // Given
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockVoiceChannel);
        when(mockDatabase.incrementVoiceJoinCount(123L)).thenReturn(1L);

        // When
        listener.onGuildVoiceUpdate(mockEvent);

        // Then
        verify(mockDatabase).incrementVoiceJoinCount(123L);
    }

    @Test
    void shouldNotHandleVoiceChannelLeave() {
        // Given
        when(mockEvent.getChannelLeft()).thenReturn(mockVoiceChannel);
        when(mockEvent.getChannelJoined()).thenReturn(null);

        // When
        listener.onGuildVoiceUpdate(mockEvent);

        // Then
        verify(mockDatabase, never()).incrementVoiceJoinCount(anyLong());
    }

    @Test
    void shouldSendMilestoneForFirstJoin() {
        // Given
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockVoiceChannel);
        when(mockDatabase.incrementVoiceJoinCount(123L)).thenReturn(1L);
        when(mockJda.getTextChannelById(BOT_MESSAGE_CHANNEL_ID)).thenReturn(mockTextChannel);
        when(mockTextChannel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(mockMessageAction);

        // When
        listener.onGuildVoiceUpdate(mockEvent);

        // Then
        verify(mockTextChannel).sendMessageEmbeds(any(MessageEmbed.class));
    }

    @Test
    void shouldSendMilestoneFor100thJoin() {
        // Given
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockVoiceChannel);
        when(mockDatabase.incrementVoiceJoinCount(123L)).thenReturn(100L);
        when(mockJda.getTextChannelById(BOT_MESSAGE_CHANNEL_ID)).thenReturn(mockTextChannel);
        when(mockTextChannel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(mockMessageAction);

        // When
        listener.onGuildVoiceUpdate(mockEvent);

        // Then
        verify(mockTextChannel).sendMessageEmbeds(any(MessageEmbed.class));
    }

    @Test
    void shouldNotSendMessageForNonMilestone() {
        // Given
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockVoiceChannel);
        when(mockDatabase.incrementVoiceJoinCount(123L)).thenReturn(50L); // Not a milestone

        // When
        listener.onGuildVoiceUpdate(mockEvent);

        // Then
        verify(mockDatabase).incrementVoiceJoinCount(123L);
        verify(mockJda, never()).getTextChannelById(anyLong());
    }

    @Test
    void shouldHandleMissingBotChannel() {
        // Given
        when(mockEvent.getChannelLeft()).thenReturn(null);
        when(mockEvent.getChannelJoined()).thenReturn(mockVoiceChannel);
        when(mockDatabase.incrementVoiceJoinCount(123L)).thenReturn(1L);
        when(mockJda.getTextChannelById(BOT_MESSAGE_CHANNEL_ID)).thenReturn(null);

        // When
        listener.onGuildVoiceUpdate(mockEvent);

        // Then
        verify(mockDatabase).incrementVoiceJoinCount(123L);
        verify(mockJda).getTextChannelById(BOT_MESSAGE_CHANNEL_ID);
    }

    @Test
    void shouldTestAllMilestones() {
        // Given
        int[] expectedMilestones = {1, 100, 500, 1000, 2000, 5000};

        for (int milestone : expectedMilestones) {
            // Reset specific mocks for each milestone
            reset(mockDatabase, mockJda, mockTextChannel, mockMessageAction);

            when(mockEvent.getChannelLeft()).thenReturn(null);
            when(mockEvent.getChannelJoined()).thenReturn(mockVoiceChannel);
            when(mockDatabase.incrementVoiceJoinCount(123L)).thenReturn((long) milestone);
            when(mockJda.getTextChannelById(BOT_MESSAGE_CHANNEL_ID)).thenReturn(mockTextChannel);
            when(mockTextChannel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(mockMessageAction);

            // When
            listener.onGuildVoiceUpdate(mockEvent);

            // Then
            verify(mockTextChannel).sendMessageEmbeds(any(MessageEmbed.class));
        }
    }

    @Test
    void shouldHandleChannelMoveAsNonJoin() {
        // Given
        AudioChannelUnion mockLeftChannel = mock(AudioChannelUnion.class);
        when(mockEvent.getChannelLeft()).thenReturn(mockLeftChannel);
        when(mockEvent.getChannelJoined()).thenReturn(mockVoiceChannel);

        // When
        listener.onGuildVoiceUpdate(mockEvent);

        // Then
        verify(mockDatabase, never()).incrementVoiceJoinCount(anyLong());
    }
}
