package org.fitznet.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MilkCommandsTest {

    @Mock
    private SlashCommandInteractionEvent mockEvent;

    @Mock
    private Guild mockGuild;

    @Mock
    private User mockUser;

    @Mock
    private ReplyCallbackAction mockReplyAction;

    private MilkCommands milkCommands;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        milkCommands = new MilkCommands();

        when(mockEvent.getName()).thenReturn("milk");
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockEvent.getUser()).thenReturn(mockUser);
        when(mockGuild.getName()).thenReturn("Test Guild");
        when(mockUser.getName()).thenReturn("TestUser");
    }

    @Test
    void testGetCommands_ReturnsCorrectSlashCommand() {
        SlashCommandData[] commands = MilkCommands.getCommands();

        assertEquals(1, commands.length);
        assertEquals("milk", commands[0].getName());
        assertEquals("Get a random picture of milk", commands[0].getDescription());
    }

    @Test
    void testGetRandomMilkImage_ReturnsValidUrl() {
        String imageUrl = MilkCommands.getRandomMilkImage();

        assertNotNull(imageUrl);
        assertTrue(imageUrl.startsWith("https://"));
        assertTrue(MilkCommands.getMilkImages().contains(imageUrl));
    }

    @Test
    void testGetMilkImages_ReturnsNonEmptyList() {
        List<String> images = MilkCommands.getMilkImages();

        assertNotNull(images);
        assertFalse(images.isEmpty());
    }

    @Test
    void testGetMilkImages_AllUrlsAreValid() {
        List<String> images = MilkCommands.getMilkImages();

        for (String url : images) {
            assertNotNull(url);
            assertTrue(url.startsWith("https://"), "URL should start with https://: " + url);
        }
    }

    @Test
    void testOnSlashCommandInteraction_IgnoresNonMilkCommands() {
        when(mockEvent.getName()).thenReturn("other");

        milkCommands.onSlashCommandInteraction(mockEvent);

        // Verify no reply was sent
        verify(mockEvent, never()).replyEmbeds(any(MessageEmbed.class));
    }

    @Test
    void testOnSlashCommandInteraction_SendsEmbedWithMilkImage() {
        when(mockEvent.replyEmbeds(any(MessageEmbed.class))).thenReturn(mockReplyAction);
        doNothing().when(mockReplyAction).queue();

        milkCommands.onSlashCommandInteraction(mockEvent);

        // Capture the embed that was sent
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(mockEvent).replyEmbeds(embedCaptor.capture());
        verify(mockReplyAction).queue();

        // Verify embed contents
        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed);
        assertEquals("🥛 Random Milk Image", embed.getTitle());
        assertNotNull(embed.getImage());
        assertTrue(MilkCommands.getMilkImages().contains(embed.getImage().getUrl()));
    }
}
