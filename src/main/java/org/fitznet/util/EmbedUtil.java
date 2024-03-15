package org.fitznet.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;

import java.awt.Color;

public class EmbedUtil {

    /**
     * Creates a reusable embed for milestone notifications.
     *
     * @param member The member who reached the milestone.
     * @param milestoneCount The count of the milestone reached.
     * @return EmbedBuilder A pre-configured EmbedBuilder instance.
     */
    public static EmbedBuilder createMilestoneEmbed(Member member, long milestoneCount) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("FITZ - NET Milestone Reached!");
        embed.setDescription(String.format("Congratulations %s! ", member.getAsMention()));
        embed.addField("Milestone Achieved", String.format("You've joined FITZ-NET **%d times**!", milestoneCount), false);
        embed.setColor(Color.decode("#1F8B4C")); // You can use Color.decode for hex colors
        embed.setThumbnail(member.getUser().getEffectiveAvatarUrl()); // Set the user's avatar as the thumbnail

        // Optionally, add a footer, timestamp, etc.
        embed.setFooter("Fitz-Net Milestone Achiever 1.0", member.getGuild().getIconUrl());
        embed.setTimestamp(java.time.Instant.now());

        return embed;
    }
}
