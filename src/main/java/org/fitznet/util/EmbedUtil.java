package org.fitznet.util;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class EmbedUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    /**
     * Creates a reusable embed for milestone notifications.
     *
     * @param member         The member who reached the milestone.
     * @param milestoneCount The count of the milestone reached.
     * @return MessageEmbed A pre-configured MessageEmbed object.
     */
    public static MessageEmbed createMilestoneEmbed(Member member, long milestoneCount) {
        return createMilestoneEmbed(member, milestoneCount, null);
    }

    /**
     * Creates a reusable embed for milestone notifications with tracking start date.
     *
     * @param member         The member who reached the milestone.
     * @param milestoneCount The count of the milestone reached.
     * @param trackingStartDate The date when tracking started for this guild.
     * @return MessageEmbed A pre-configured MessageEmbed object.
     */
    public static MessageEmbed createMilestoneEmbed(Member member, long milestoneCount, LocalDateTime trackingStartDate) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("FITZ - NET Milestone Reached!");
        embed.setDescription(String.format("Congratulations %s! ", member.getAsMention()));

        String milestoneText = String.format("You've joined FITZ-NET **%d time(s)**!", milestoneCount);
        if (trackingStartDate != null) {
            String formattedDate = trackingStartDate.format(DATE_FORMATTER);
            milestoneText += String.format("\n*(As of %s)*", formattedDate);
        }

        embed.addField("Milestone Achieved", milestoneText, false);
        embed.setColor(Color.decode("#1F8B4C")); // You can use Color.decode for hex colors
        embed.setThumbnail(member.getUser().getEffectiveAvatarUrl()); // Set the user's avatar as the thumbnail

        // Optionally, add a footer, timestamp, etc.
        embed.setFooter("Fitz-Net Milestone Achiever 1.0", member.getGuild().getIconUrl());
        embed.setTimestamp(java.time.Instant.now());
        return embed.build();
    }
}
