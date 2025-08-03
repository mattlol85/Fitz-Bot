package org.fitznet.util;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.awt.Color;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class EmbedUtilTest {

    @Mock
    private Member mockMember;

    @Mock
    private User mockUser;

    @Mock
    private Guild mockGuild;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mockMember.getAsMention()).thenReturn("<@123456789>");
        when(mockMember.getUser()).thenReturn(mockUser);
        when(mockMember.getGuild()).thenReturn(mockGuild);
        when(mockUser.getEffectiveAvatarUrl()).thenReturn("https://example.com/avatar.png");
        when(mockGuild.getIconUrl()).thenReturn("https://example.com/guild-icon.png");
    }

    @Test
    void testCreateMilestoneEmbedWithoutTrackingDate() {
        MessageEmbed embed = EmbedUtil.createMilestoneEmbed(mockMember, 5);

        assertNotNull(embed);
        assertEquals("FITZ - NET Milestone Reached!", embed.getTitle());
        assertEquals("Congratulations <@123456789>! ", embed.getDescription());
        assertEquals(Color.decode("#1F8B4C"), embed.getColor());

        assertEquals(1, embed.getFields().size());
        MessageEmbed.Field milestoneField = embed.getFields().get(0);
        assertEquals("Milestone Achieved", milestoneField.getName());
        assertEquals("You've joined FITZ-NET **5 time(s)**!", milestoneField.getValue());
        assertFalse(milestoneField.isInline());

        assertNotNull(embed.getThumbnail());
        assertEquals("https://example.com/avatar.png", embed.getThumbnail().getUrl());

        assertNotNull(embed.getFooter());
        assertEquals("Fitz-Net Milestone Achiever 1.0", embed.getFooter().getText());
        assertEquals("https://example.com/guild-icon.png", embed.getFooter().getIconUrl());

        assertNotNull(embed.getTimestamp());
    }

    @Test
    void testCreateMilestoneEmbedWithTrackingDate() {
        LocalDateTime trackingDate = LocalDateTime.of(2024, 1, 15, 10, 30);
        MessageEmbed embed = EmbedUtil.createMilestoneEmbed(mockMember, 10, trackingDate);

        assertNotNull(embed);
        assertEquals("FITZ - NET Milestone Reached!", embed.getTitle());
        assertEquals("Congratulations <@123456789>! ", embed.getDescription());

        assertEquals(1, embed.getFields().size());
        MessageEmbed.Field milestoneField = embed.getFields().get(0);
        assertEquals("Milestone Achieved", milestoneField.getName());

        String expectedValue = "You've joined FITZ-NET **10 time(s)**!\n*(Tracking started Jan 15, 2024)*";
        assertEquals(expectedValue, milestoneField.getValue());
        assertFalse(milestoneField.isInline());
    }

    @Test
    void testCreateMilestoneEmbedWithNullTrackingDate() {
        MessageEmbed embed = EmbedUtil.createMilestoneEmbed(mockMember, 3, null);

        assertNotNull(embed);
        assertEquals(1, embed.getFields().size());
        MessageEmbed.Field milestoneField = embed.getFields().get(0);
        assertEquals("You've joined FITZ-NET **3 time(s)**!", milestoneField.getValue());

        // Add null check to prevent NullPointerException
        String fieldValue = milestoneField.getValue();
        assertNotNull(fieldValue, "Field value should not be null");
        assertFalse(fieldValue.contains("Tracking started"));
    }

    @Test
    void testBackwardCompatibilityOverload() {
        MessageEmbed embedWithoutDate = EmbedUtil.createMilestoneEmbed(mockMember, 7);
        MessageEmbed embedWithNullDate = EmbedUtil.createMilestoneEmbed(mockMember, 7, null);

        assertEquals(embedWithoutDate.getTitle(), embedWithNullDate.getTitle());
        assertEquals(embedWithoutDate.getDescription(), embedWithNullDate.getDescription());
        assertEquals(embedWithoutDate.getFields().get(0).getValue(),
                    embedWithNullDate.getFields().get(0).getValue());
    }

    @Test
    void testDifferentMilestoneNumbers() {
        MessageEmbed embed1 = EmbedUtil.createMilestoneEmbed(mockMember, 1);
        MessageEmbed embed100 = EmbedUtil.createMilestoneEmbed(mockMember, 100);

        assertTrue(embed1.getFields().get(0).getValue().contains("**1 time(s)**"));
        assertTrue(embed100.getFields().get(0).getValue().contains("**100 time(s)**"));
    }

    @Test
    void testDateFormatting() {
        LocalDateTime[] testDates = {
            LocalDateTime.of(2024, 1, 1, 0, 0),
            LocalDateTime.of(2024, 12, 31, 23, 59),
            LocalDateTime.of(2023, 6, 15, 12, 30)
        };

        String[] expectedFormats = {
            "Jan 01, 2024",
            "Dec 31, 2024",
            "Jun 15, 2023"
        };

        for (int i = 0; i < testDates.length; i++) {
            MessageEmbed embed = EmbedUtil.createMilestoneEmbed(mockMember, 5, testDates[i]);
            String fieldValue = embed.getFields().get(0).getValue();
            assertTrue(fieldValue.contains(expectedFormats[i]),
                      "Expected date format '" + expectedFormats[i] + "' not found in: " + fieldValue);
        }
    }
}
