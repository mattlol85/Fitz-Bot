package org.fitznet.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.awt.Color;
import java.util.List;
import java.util.Random;

/**
 * Slash command handler for the /milk command that returns a random milk image.
 */
@Slf4j
public class MilkCommands extends ListenerAdapter {

    private static final Random RANDOM = new Random();

    /**
     * List of milk-related image URLs.
     */
    private static final List<String> MILK_IMAGES = List.of(
            "https://upload.wikimedia.org/wikipedia/commons/0/0e/Milk_glass.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/a/a5/Glass_of_Milk_%2833657535532%29.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/004-soymilk.jpg/1024px-004-soymilk.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Tasnim-chocolate-milk.jpg/1200px-Tasnim-chocolate-milk.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Big_Glass_of_Milk.jpg/1200px-Big_Glass_of_Milk.jpg"
    );

    /**
     * Gets the slash command definitions.
     */
    public static SlashCommandData[] getCommands() {
        return new SlashCommandData[]{
                Commands.slash("milk", "Get a random picture of milk")
        };
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("milk")) {
            return;
        }

        log.info("Received /milk command from user: {} in guild: {}",
                event.getUser().getName(),
                event.getGuild() != null ? event.getGuild().getName() : "DM");

        try {
            String randomMilkImage = getRandomMilkImage();

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("🥛 Random Milk Image");
            embed.setImage(randomMilkImage);
            embed.setColor(Color.WHITE);
            embed.setFooter("Enjoy your milk!");

            event.replyEmbeds(embed.build()).queue();

            log.info("Successfully sent milk image to user: {}", event.getUser().getName());

        } catch (Exception e) {
            log.error("Error in /milk command", e);
            event.reply("❌ An error occurred while fetching a milk image.")
                    .setEphemeral(true).queue();
        }
    }

    /**
     * Gets a random milk image URL from the list.
     *
     * @return A random milk image URL.
     */
    String getRandomMilkImage() {
        return MILK_IMAGES.get(RANDOM.nextInt(MILK_IMAGES.size()));
    }

    /**
     * Gets the list of milk images (for testing purposes).
     *
     * @return The list of milk image URLs.
     */
    static List<String> getMilkImages() {
        return MILK_IMAGES;
    }
}
