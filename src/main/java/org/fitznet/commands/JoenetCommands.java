package org.fitznet.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.fitznet.dto.radarr.MovieSearchResponseDto;
import org.fitznet.service.RadarrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Slash command handler for JoeNet media download commands.
 */
@Slf4j
@Component
public class JoenetCommands extends ListenerAdapter {

    @Autowired
    private RadarrService radarrService;

    /**
     * Gets the slash command definitions.
     */
    public static SlashCommandData[] getCommands() {
        return new SlashCommandData[]{
                Commands.slash("joenet", "Download movies or TV shows from JoeNet")
                        .addSubcommands(
                                new SubcommandData("download", "Search and download movies or TV shows")
                        )
        };
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("joenet")) {
            return;
        }

        log.info("Received /joenet command from user: {} in guild: {}",
                event.getUser().getName(),
                event.getGuild() != null ? event.getGuild().getName() : "DM");

        try {
            if (!event.isAcknowledged()) {
                event.deferReply(true).queue();
            }

            if (!event.isFromGuild()) {
                event.getHook().editOriginal("❌ This command can only be used in a server!").queue();
                return;
            }

            String subcommand = event.getSubcommandName();
            if ("download".equals(subcommand)) {
                handleDownloadCommand(event);
            } else {
                event.getHook().editOriginal("❌ Unknown subcommand").queue();
            }

        } catch (Exception e) {
            log.error("Error in onSlashCommandInteraction for /joenet", e);
            try {
                if (event.isAcknowledged()) {
                    event.getHook().editOriginal("❌ An error occurred while processing your command.").queue();
                } else {
                    event.reply("❌ An error occurred while processing your command.")
                            .setEphemeral(true).queue();
                }
            } catch (Exception replyError) {
                log.error("Failed to send error response", replyError);
            }
        }
    }

    private void handleDownloadCommand(SlashCommandInteractionEvent event) {
        log.info("Processing /joenet download command");

        Button moviesButton = Button.primary("joenet:movies", "🎬 Movies");
        Button tvButton = Button.secondary("joenet:tv", "📺 TV Shows (Coming Soon)").asDisabled();

        event.getHook().editOriginal("Select the type of media you want to download:")
                .setActionRow(moviesButton, tvButton)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        if (!buttonId.startsWith("joenet:")) {
            return;
        }

        log.info("Button interaction: {} from user: {}", buttonId, event.getUser().getName());

        try {
            if ("joenet:movies".equals(buttonId)) {
                handleMoviesButton(event);
            } else if ("joenet:tv".equals(buttonId)) {
                event.reply("📺 TV show downloads are not yet implemented. Stay tuned!")
                        .setEphemeral(true).queue();
            }
        } catch (Exception e) {
            log.error("Error handling button interaction: {}", buttonId, e);
            event.reply("❌ An error occurred while processing your request.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleMoviesButton(ButtonInteractionEvent event) {
        log.info("User selected Movies button");

        TextInput searchInput = TextInput.create("search-term", "Movie Name", TextInputStyle.SHORT)
                .setPlaceholder("Enter movie name (e.g., Wicked)")
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(100)
                .build();

        Modal modal = Modal.create("joenet:search", "Search for Movies")
                .addActionRow(searchInput)
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        if (!modalId.startsWith("joenet:")) {
            return;
        }

        log.info("Modal interaction: {} from user: {}", modalId, event.getUser().getName());

        try {
            if ("joenet:search".equals(modalId)) {
                handleSearchModal(event);
            }
        } catch (Exception e) {
            log.error("Error handling modal interaction: {}", modalId, e);
            event.reply("❌ An error occurred while searching for movies.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleSearchModal(ModalInteractionEvent event) {
        String searchTerm = event.getValue("search-term").getAsString().trim();
        log.info("Searching for movies with term: {}", searchTerm);

        event.deferReply(true).queue();

        // Search for movies using Radarr API
        List<MovieSearchResponseDto> results = radarrService.searchMovies(searchTerm);

        if (results.isEmpty()) {
            event.getHook().editOriginal(
                    String.format("❌ No movies found for '%s'. Try a different search term.", searchTerm)
            ).queue();
            return;
        }

        // Build select menu with results
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("joenet:select")
                .setPlaceholder("Select a movie to download");

        for (MovieSearchResponseDto movie : results) {
            String label = String.format("%s (%d)", movie.getTitle(), movie.getYear());
            String description = buildMovieDescription(movie);
            String value = String.format("%d:%s", movie.getTmdbId(), movie.getTitle());

            menuBuilder.addOption(label, value, description);
        }

        event.getHook().editOriginal(String.format("Found %d movie(s) for '%s':", results.size(), searchTerm))
                .setActionRow(menuBuilder.build())
                .queue();
    }

    private String buildMovieDescription(MovieSearchResponseDto movie) {
        StringBuilder description = new StringBuilder();

        // Add first 2 genres if available
        if (movie.getGenres() != null && !movie.getGenres().isEmpty()) {
            String genres = movie.getGenres().stream()
                    .limit(2)
                    .collect(Collectors.joining(", "));
            description.append(genres);
        }

        // Truncate to fit Discord's 100 character limit for select option descriptions
        String result = description.toString();
        if (result.length() > 100) {
            result = result.substring(0, 97) + "...";
        }

        return result.isEmpty() ? "Movie" : result;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String selectId = event.getComponentId();

        if (!selectId.startsWith("joenet:")) {
            return;
        }

        log.info("Select interaction: {} from user: {}", selectId, event.getUser().getName());

        try {
            if ("joenet:select".equals(selectId)) {
                handleMovieSelection(event);
            }
        } catch (Exception e) {
            log.error("Error handling select interaction: {}", selectId, e);
            event.reply("❌ An error occurred while adding the movie.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleMovieSelection(StringSelectInteractionEvent event) {
        String selectedValue = event.getValues().get(0);
        log.info("User selected movie: {}", selectedValue);

        // Parse tmdbId and title from value (format: "tmdbId:title")
        String[] parts = selectedValue.split(":", 2);
        if (parts.length != 2) {
            event.reply("❌ Invalid movie selection.").setEphemeral(true).queue();
            return;
        }

        int tmdbId;
        try {
            tmdbId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            event.reply("❌ Invalid movie ID.").setEphemeral(true).queue();
            return;
        }

        String movieTitle = parts[1];

        event.deferReply(true).queue();

        // Add movie to Radarr
        boolean success = radarrService.downloadMovie(tmdbId, movieTitle);

        if (success) {
            event.getHook().editOriginal(
                    String.format("✅ Successfully added **%s** to the download queue!\n" +
                            "The movie will be downloaded automatically.", movieTitle)
            ).queue();
        } else {
            event.getHook().editOriginal(
                    String.format("❌ Failed to add **%s** to the download queue.\n" +
                            "The movie may already exist in your library, or there was an error communicating with Radarr.", movieTitle)
            ).queue();
        }
    }
}

