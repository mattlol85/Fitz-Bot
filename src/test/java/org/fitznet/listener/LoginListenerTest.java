package org.fitznet.listener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.fitznet.util.JsonUtilsTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Objects;
import java.util.function.Consumer;

import static org.fitznet.util.Constants.BOT_MESSAGE_CHANNEL_ID;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class LoginListenerTest {

    private JDA mockJda;
    private TextChannel mockTextChannel;
    private Member mockMember;
    private Guild mockGuild;
    private LoginListener listener;

    @BeforeEach
    public void setUp() {
        JsonUtilsTest.removeTestUser(123L);
        // Setup mock objects
        mockJda = mock(JDA.class);
        mockTextChannel = mock(TextChannel.class);
        mockMember = mock(Member.class);
        mockGuild = mock(Guild.class);
        User mockUser = mock(User.class);
        MessageCreateAction mockMessageCreateAction = mock(MessageCreateAction.class);


        // Initialize LoginListener with mock JDA object
        listener = new LoginListener(mockJda);

        // Mocking the necessary methods
        when(mockTextChannel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(mockMessageCreateAction);
        doNothing().when(mockMessageCreateAction).queue(any(Consumer.class), any(Consumer.class));

        when(mockJda.getTextChannelById(BOT_MESSAGE_CHANNEL_ID)).thenReturn(mockTextChannel);
        when(mockGuild.getName()).thenReturn("Dummy Guild");
        when(mockGuild.getIconUrl()).thenReturn("https://example.com/guildIcon.jpg");
        when(mockMember.getGuild()).thenReturn(mockGuild);
        when(mockMember.getUser()).thenReturn(mockUser);
        when(mockMember.getAsMention()).thenReturn("@DummyUser");
        when(mockUser.getEffectiveAvatarUrl()).thenReturn("https://example.com/avatar.jpg");
    }

    @Test
    public void shouldSendMilestoneForFirstJoin() {
        // Given
        GuildVoiceUpdateEvent mockEvent = mock(GuildVoiceUpdateEvent.class);
        when(mockEvent.getChannelLeft()).thenReturn(null); // Simulate joining a channel
        when(mockEvent.getMember()).thenReturn(mockMember);
        when(mockMember.getIdLong()).thenReturn(123L); // Use a specific member ID for testing
        when(mockMember.getGuild()).thenReturn(mockGuild);
        when(mockEvent.getGuild()).thenReturn(mockGuild);

        // Capture the embed sent to the channel
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);

        // When
        listener.onGuildVoiceUpdate(mockEvent);

        // Then
        verify(mockTextChannel, times(1)).sendMessageEmbeds(embedCaptor.capture());
        MessageEmbed sentEmbed = embedCaptor.getValue();

        assertNotNull(sentEmbed);
        assertTrue(Objects.requireNonNull(sentEmbed.getDescription()).contains("Congratulations"));
    }

    @Test
    public void shouldSendMilestoneFor100thJoin() {
        // Given
        GuildVoiceUpdateEvent mockEvent = mock(GuildVoiceUpdateEvent.class);
        when(mockEvent.getChannelLeft()).thenReturn(null); // Simulate joining a channel
        when(mockEvent.getMember()).thenReturn(mockMember);
        when(mockMember.getIdLong()).thenReturn(123L); // Use a specific member ID for testing
        when(mockMember.getGuild()).thenReturn(mockGuild);
        when(mockEvent.getGuild()).thenReturn(mockGuild); // Return mockGuild when getGuild() is called

        // Capture the embed sent to the channel
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);

        // When
        for (int i = 0; i < 100; i++) {
            listener.onGuildVoiceUpdate(mockEvent);
        }
        // Then
        verify(mockTextChannel, times(2)).sendMessageEmbeds(embedCaptor.capture());
        MessageEmbed sentEmbed = embedCaptor.getValue();

        assertNotNull(sentEmbed);
        assertTrue(Objects.requireNonNull(sentEmbed.getDescription()).contains("Congratulations"));
    }
}
