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
import net.dv8tion.jda.api.EmbedBuilder;
import org.fitznet.dto.radarr.MovieSearchResponseDto;
import org.fitznet.dto.radarr.RadarrQueueItemDto;
import org.fitznet.dto.sonarr.EpisodeDto;
import org.fitznet.dto.sonarr.Season;
import org.fitznet.dto.sonarr.SeriesSearchResponseDto;
import org.fitznet.dto.sonarr.SonarrQueueItemDto;
import org.fitznet.dto.sonarr.SonarrSeriesDto;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.fitznet.data.GuildConfigDatabase;
import org.fitznet.service.RadarrService;
import org.fitznet.service.SonarrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
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

    @Autowired
    private SonarrService sonarrService;

    private GuildConfigDatabase configDatabase = new GuildConfigDatabase();

    /**
     * Gets the slash command definitions.
     */
    public static SlashCommandData[] getCommands() {
        return new SlashCommandData[]{
                Commands.slash("joenet", "Download movies or TV shows from JoeNet")
                        .addSubcommands(
                                new SubcommandData("download", "Search and download movies or TV shows"),
                                new SubcommandData("status", "View the current JoeNet download queue")
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
            } else if ("status".equals(subcommand)) {
                handleStatusCommand(event);
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

    private void handleStatusCommand(SlashCommandInteractionEvent event) {
        log.info("Processing /joenet status command for user: {}", event.getUser().getName());

        List<RadarrQueueItemDto> radarrItems = null;
        List<SonarrQueueItemDto> sonarrItems = null;
        boolean radarrError = false;
        boolean sonarrError = false;

        try {
            radarrItems = radarrService.getQueueDetails();
        } catch (Exception e) {
            log.warn("Failed to fetch Radarr queue details: {}", e.getMessage());
            radarrError = true;
        }

        try {
            sonarrItems = sonarrService.getQueueDetails();
        } catch (Exception e) {
            log.warn("Failed to fetch Sonarr queue details: {}", e.getMessage());
            sonarrError = true;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("JoeNet Download Queue");
        embed.setColor(Color.decode("#3498DB"));

        String radarrField = formatQueueSection(radarrItems, radarrError);
        String sonarrField = formatQueueSection(sonarrItems, sonarrError);

        embed.addField("🎬 Radarr (Movies)", radarrField, false);
        embed.addField("📺 Sonarr (TV Shows)", sonarrField, false);
        embed.setFooter("JoeNet Download Status");

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private String formatQueueSection(List<?> items, boolean error) {
        if (error) {
            return "⚠️ Service unavailable";
        }
        if (items == null || items.isEmpty()) {
            return "✅ Queue is empty";
        }

        List<?> activeItems = items.stream()
                .filter(item -> {
                    String s = item instanceof RadarrQueueItemDto r ? r.getStatus()
                             : item instanceof SonarrQueueItemDto sq ? sq.getStatus() : null;
                    return s == null || !s.equalsIgnoreCase("completed");
                })
                .toList();

        if (activeItems.isEmpty()) {
            return "✅ Queue is empty";
        }

        int cap = Math.min(activeItems.size(), 10);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < cap; i++) {
            Object rawItem = activeItems.get(i);
            String title;
            String status;
            String trackedStatus;
            Double size;
            Double sizeleft;
            String eta;

            if (rawItem instanceof RadarrQueueItemDto item) {
                title = item.getTitle();
                status = item.getStatus();
                trackedStatus = item.getTrackedDownloadStatus();
                size = item.getSize();
                sizeleft = item.getSizeleft();
                eta = item.getEstimatedCompletionTime();
            } else if (rawItem instanceof SonarrQueueItemDto item) {
                title = item.getTitle();
                status = item.getStatus();
                trackedStatus = item.getTrackedDownloadStatus();
                size = item.getSize();
                sizeleft = item.getSizeleft();
                eta = item.getEstimatedCompletionTime();
            } else {
                continue;
            }

            String displayTitle = (title != null && title.length() > 40)
                    ? title.substring(0, 37) + "..."
                    : (title != null ? title : "Unknown");

            String statusEmoji = getStatusEmoji(status, trackedStatus);

            sb.append("• **").append(displayTitle).append("** — ").append(statusEmoji).append(" ").append(capitalise(status));

            // Show download progress percentage
            if (size != null && sizeleft != null && size > 0) {
                double progress = (size - sizeleft) / size * 100.0;
                sb.append(String.format(" (%.0f%%)", progress));
            }

            // Show ETA
            if (eta != null && !eta.isEmpty()) {
                try {
                    OffsetDateTime etaTime = OffsetDateTime.parse(eta);
                    Duration remaining = Duration.between(OffsetDateTime.now(), etaTime);
                    if (!remaining.isNegative()) {
                        long hours = remaining.toHours();
                        long minutes = remaining.toMinutesPart();
                        if (hours > 0) {
                            sb.append(String.format(" — %dh %dm left", hours, minutes));
                        } else {
                            sb.append(String.format(" — %dm left", minutes));
                        }
                    }
                } catch (Exception ignored) {
                    // ETA not parseable — skip it
                }
            }

            sb.append("\n");
        }

        if (activeItems.size() > cap) {
            sb.append("*…and ").append(activeItems.size() - cap).append(" more*");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "✅ Queue is empty" : result;
    }

    private String getStatusEmoji(String status, String trackedStatus) {
        if (status == null) return "🔄";
        return switch (status.toLowerCase()) {
            case "downloading" -> "⬇️";
            case "queued"      -> "⏳";
            case "completed"   -> "✅";
            case "failed"      -> "❌";
            case "paused"      -> "⏸";
            case "warning"     -> "⚠️";
            default            -> "🔄";
        };
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private void handleDownloadCommand(SlashCommandInteractionEvent event) {
        log.info("Processing /joenet download command");

        Button moviesButton = Button.primary("joenet:movies", "🎬 Movies");
        Button tvButton = Button.secondary("joenet:tv", "📺 TV Shows");

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
                handleTvButton(event);
            } else if (buttonId.startsWith("joenet:specificepisode:")) {
                handleSpecificEpisodeButton(event);
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

        Modal modal = Modal.create("joenet:search:movie", "Search for Movies")
                .addActionRow(searchInput)
                .build();

        event.replyModal(modal).queue();
    }

    private void handleTvButton(ButtonInteractionEvent event) {
        log.info("User selected TV Shows button");

        TextInput searchInput = TextInput.create("search-term", "TV Show Name", TextInputStyle.SHORT)
                .setPlaceholder("Enter TV show name (e.g., Breaking Bad)")
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(100)
                .build();

        Modal modal = Modal.create("joenet:search:tv", "Search for TV Shows")
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
            if ("joenet:search:movie".equals(modalId)) {
                handleMovieSearchModal(event);
            } else if ("joenet:search:tv".equals(modalId)) {
                handleTvSearchModal(event);
            }
        } catch (Exception e) {
            log.error("Error handling modal interaction: {}", modalId, e);
            event.reply("❌ An error occurred while searching for movies.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleMovieSearchModal(ModalInteractionEvent event) {
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
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("joenet:select:movie")
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

    private void handleTvSearchModal(ModalInteractionEvent event) {
        String searchTerm = event.getValue("search-term").getAsString().trim();
        log.info("Searching for TV shows with term: {}", searchTerm);

        event.deferReply(true).queue();

        // Search for TV series using Sonarr API
        List<SeriesSearchResponseDto> results = sonarrService.searchSeries(searchTerm);

        if (results.isEmpty()) {
            event.getHook().editOriginal(
                    String.format("❌ No TV shows found for '%s'. Try a different search term.", searchTerm)
            ).queue();
            return;
        }

        // Build select menu with results
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("joenet:select:tv")
                .setPlaceholder("Select a TV show to download");

        for (SeriesSearchResponseDto series : results) {
            String label = series.getYear() != null
                    ? String.format("%s (%d)", series.getTitle(), series.getYear())
                    : series.getTitle();
            String description = buildSeriesDescription(series);
            String value = String.format("%d:%s", series.getTvdbId(), series.getTitle());

            menuBuilder.addOption(label, value, description);
        }

        event.getHook().editOriginal(String.format("Found %d TV show(s) for '%s':", results.size(), searchTerm))
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

    private String buildSeriesDescription(SeriesSearchResponseDto series) {
        StringBuilder description = new StringBuilder();

        // Add status if available
        if (series.getStatus() != null && !series.getStatus().isEmpty()) {
            description.append(series.getStatus());
        }

        // Add first 2 genres if available
        if (series.getGenres() != null && !series.getGenres().isEmpty()) {
            if (description.length() > 0) {
                description.append(" • ");
            }
            String genres = series.getGenres().stream()
                    .limit(2)
                    .collect(Collectors.joining(", "));
            description.append(genres);
        }

        // Truncate to fit Discord's 100 character limit for select option descriptions
        String result = description.toString();
        if (result.length() > 100) {
            result = result.substring(0, 97) + "...";
        }

        return result.isEmpty() ? "TV Show" : result;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String selectId = event.getComponentId();

        if (!selectId.startsWith("joenet:")) {
            return;
        }

        log.info("Select interaction: {} from user: {}", selectId, event.getUser().getName());

        try {
            if ("joenet:select:movie".equals(selectId)) {
                handleMovieSelection(event);
            } else if ("joenet:select:tv".equals(selectId)) {
                handleSeriesSelection(event);
            } else if (selectId.startsWith("joenet:seasons:")) {
                handleSeasonSelection(event);
            } else if (selectId.startsWith("joenet:episodeseason:")) {
                handleEpisodeSeasonSelection(event);
            } else if (selectId.startsWith("joenet:episodes:")) {
                handleEpisodeSelection(event);
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
            postRequesterLog(event.getGuild(), event.getUser(), movieTitle);
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

    private void handleSeriesSelection(StringSelectInteractionEvent event) {
        String selectedValue = event.getValues().get(0);
        log.info("User selected TV series: {}", selectedValue);

        // Parse tvdbId and title from value (format: "tvdbId:title")
        String[] parts = selectedValue.split(":", 2);
        if (parts.length != 2) {
            event.reply("❌ Invalid TV show selection.").setEphemeral(true).queue();
            return;
        }

        int tvdbId;
        try {
            tvdbId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            event.reply("❌ Invalid TV show ID.").setEphemeral(true).queue();
            return;
        }

        String seriesTitle = parts[1];

        event.deferReply(true).queue();

        // Search for the series again to get season information
        List<SeriesSearchResponseDto> results = sonarrService.searchSeries(seriesTitle);
        SeriesSearchResponseDto selectedSeries = results.stream()
                .filter(s -> s.getTvdbId().equals(tvdbId))
                .findFirst()
                .orElse(null);

        if (selectedSeries == null || selectedSeries.getSeasons() == null || selectedSeries.getSeasons().isEmpty()) {
            event.getHook().editOriginal("❌ Could not retrieve season information for this show.").queue();
            return;
        }

        List<Season> seasons = selectedSeries.getSeasons();

        // Filter out season 0 (specials) if present
        seasons = seasons.stream()
                .filter(s -> s.getSeasonNumber() > 0)
                .collect(Collectors.toList());

        if (seasons.isEmpty()) {
            event.getHook().editOriginal("❌ No seasons available for this show.").queue();
            return;
        }

        // Build season selection menu
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("joenet:seasons:" + tvdbId + ":" + seriesTitle)
                .setPlaceholder("Select seasons to download")
                .setMinValues(1)
                .setMaxValues(Math.min(seasons.size() + 1, 25)); // +1 for "All Seasons" option, max 25

        // Add "All Seasons" option
        menuBuilder.addOption("All Seasons", "all", "Download all available seasons");

        // Add individual season options (limit to 24 to stay under Discord's 25 option limit)
        for (Season season : seasons.stream().limit(24).collect(Collectors.toList())) {
            String label = "Season " + season.getSeasonNumber();
            String value = String.valueOf(season.getSeasonNumber());
            menuBuilder.addOption(label, value);
        }

        event.getHook().editOriginal(String.format("**%s** has %d season(s). Select which seasons to download:",
                seriesTitle, seasons.size()))
                .setComponents(
                        net.dv8tion.jda.api.interactions.components.ActionRow.of(menuBuilder.build()),
                        net.dv8tion.jda.api.interactions.components.ActionRow.of(
                                Button.secondary(buildSpecificEpisodeButtonId(tvdbId, seriesTitle),
                                        "🎯 Specific Episode")
                        )
                )
                .queue();
    }

    private void handleSeasonSelection(StringSelectInteractionEvent event) {
        String selectId = event.getComponentId();
        log.info("User selected seasons with ID: {}", selectId);

        // Parse tvdbId and title from selectId (format: "joenet:seasons:tvdbId:title")
        String[] parts = selectId.split(":", 4);
        if (parts.length != 4) {
            event.reply("❌ Invalid season selection.").setEphemeral(true).queue();
            return;
        }

        int tvdbId;
        try {
            tvdbId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            event.reply("❌ Invalid TV show ID.").setEphemeral(true).queue();
            return;
        }

        String seriesTitle = parts[3];
        List<String> selectedValues = event.getValues();

        event.deferReply(true).queue();

        // Search for the series again to get full season information
        List<SeriesSearchResponseDto> results = sonarrService.searchSeries(seriesTitle);
        SeriesSearchResponseDto selectedSeries = results.stream()
                .filter(s -> s.getTvdbId().equals(tvdbId))
                .findFirst()
                .orElse(null);

        if (selectedSeries == null || selectedSeries.getSeasons() == null) {
            event.getHook().editOriginal("❌ Could not retrieve season information for this show.").queue();
            return;
        }

        List<Season> seasonsToDownload = new ArrayList<>();

        if (selectedValues.contains("all")) {
            // Monitor all seasons (except season 0)
            seasonsToDownload = selectedSeries.getSeasons().stream()
                    .filter(s -> s.getSeasonNumber() > 0)
                    .map(s -> new Season(s.getSeasonNumber(), true))
                    .collect(Collectors.toList());
        } else {
            // Monitor only selected seasons
            List<Integer> selectedSeasonNumbers = selectedValues.stream()
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            for (Season season : selectedSeries.getSeasons()) {
                boolean monitored = selectedSeasonNumbers.contains(season.getSeasonNumber());
                seasonsToDownload.add(new Season(season.getSeasonNumber(), monitored));
            }
        }

        // Add series to Sonarr
        boolean success = sonarrService.downloadSeries(tvdbId, seriesTitle, seasonsToDownload);

        if (success) {
            String seasonInfo = selectedValues.contains("all")
                    ? "all seasons"
                    : selectedValues.size() + " season(s)";
            postRequesterLog(event.getGuild(), event.getUser(), seriesTitle);
            event.getHook().editOriginal(
                    String.format("✅ Successfully added **%s** (%s) to the download queue!\n" +
                            "The episodes will be downloaded automatically.", seriesTitle, seasonInfo)
            ).queue();
        } else {
            event.getHook().editOriginal(
                    String.format("❌ Failed to add **%s** to the download queue.\n" +
                            "The show may already exist in your library, or there was an error communicating with Sonarr.", seriesTitle)
            ).queue();
        }
    }

    // ── Specific Episode flow ────────────────────────────────────────────────

    /**
     * Handles the "🎯 Specific Episode" button click.
     * Shows a season-picker select menu so the user can choose which season to browse episodes from.
     * Component ID format: joenet:specificepisode:{tvdbId}:{seriesTitle}
     */
    private void handleSpecificEpisodeButton(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        // format: joenet:specificepisode:{tvdbId}:{seriesTitle}
        String[] parts = buttonId.split(":", 4);
        if (parts.length != 4) {
            event.reply("❌ Invalid button state.").setEphemeral(true).queue();
            return;
        }

        int tvdbId;
        try {
            tvdbId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            event.reply("❌ Invalid TV show ID.").setEphemeral(true).queue();
            return;
        }
        String seriesTitle = parts[3];

        event.deferReply(true).queue();

        // We need the season list — re-use the series lookup
        List<SeriesSearchResponseDto> results = sonarrService.searchSeries(seriesTitle);
        SeriesSearchResponseDto series = results.stream()
                .filter(s -> s.getTvdbId() != null && s.getTvdbId().equals(tvdbId))
                .findFirst()
                .orElse(null);

        if (series == null || series.getSeasons() == null || series.getSeasons().isEmpty()) {
            event.getHook().editOriginal("❌ Could not retrieve season information for this show.").queue();
            return;
        }

        List<Season> seasons = series.getSeasons().stream()
                .filter(s -> s.getSeasonNumber() > 0)
                .collect(Collectors.toList());

        if (seasons.isEmpty()) {
            event.getHook().editOriginal("❌ No seasons available for this show.").queue();
            return;
        }

        // Build a season-picker select menu (single-select)
        StringSelectMenu.Builder menuBuilder = StringSelectMenu
                .create("joenet:episodeseason:" + tvdbId + ":" + truncateForId(seriesTitle))
                .setPlaceholder("Select a season to browse episodes")
                .setMinValues(1)
                .setMaxValues(1);

        for (Season season : seasons.stream().limit(25).collect(Collectors.toList())) {
            menuBuilder.addOption("Season " + season.getSeasonNumber(),
                    String.valueOf(season.getSeasonNumber()));
        }

        event.getHook().editOriginal(
                        String.format("Which season of **%s** would you like to browse episodes for?", seriesTitle))
                .setActionRow(menuBuilder.build())
                .queue();
    }

    /**
     * Handles the season selection for the specific-episode flow.
     * Looks up the series in the Sonarr library, fetches its episodes for the chosen season,
     * and presents them as a select menu.
     * Component ID format: joenet:episodeseason:{tvdbId}:{seriesTitle}
     */
    private void handleEpisodeSeasonSelection(StringSelectInteractionEvent event) {
        String selectId = event.getComponentId();
        // format: joenet:episodeseason:{tvdbId}:{seriesTitle}
        String[] parts = selectId.split(":", 4);
        if (parts.length != 4) {
            event.reply("❌ Invalid selection.").setEphemeral(true).queue();
            return;
        }

        int tvdbId;
        int seasonNumber;
        try {
            tvdbId = Integer.parseInt(parts[2]);
            seasonNumber = Integer.parseInt(event.getValues().get(0));
        } catch (NumberFormatException e) {
            event.reply("❌ Invalid ID.").setEphemeral(true).queue();
            return;
        }
        String seriesTitle = parts[3];

        event.deferReply(true).queue();

        // Look up the series in the Sonarr library using TVDB ID
        SonarrSeriesDto librarySeries = sonarrService.getSeriesByTvdbId(tvdbId);
        if (librarySeries == null) {
            event.getHook().editOriginal(
                    String.format("❌ **%s** is not in your Sonarr library yet.\n" +
                            "Please add it first using the **seasons option**, then use " +
                            "🎯 Specific Episode to re-trigger individual downloads.", seriesTitle)
            ).queue();
            return;
        }

        // Fetch episodes for the chosen season
        List<EpisodeDto> episodes =
                sonarrService.getEpisodes(librarySeries.getId(), seasonNumber);

        if (episodes.isEmpty()) {
            event.getHook().editOriginal(
                    String.format("❌ No episodes found for **%s** Season %d. " +
                            "The season may not have aired yet.", seriesTitle, seasonNumber)
            ).queue();
            return;
        }

        // Cap at 25 (Discord select menu limit)
        boolean truncated = episodes.size() > 25;
        List<EpisodeDto> displayEpisodes =
                truncated ? episodes.subList(0, 25) : episodes;

        // Build episode select menu (single-select)
        // Component ID: joenet:episodes:{sonarrSeriesId}
        StringSelectMenu.Builder menuBuilder = StringSelectMenu
                .create("joenet:episodes:" + librarySeries.getId())
                .setPlaceholder("Select an episode to download")
                .setMinValues(1)
                .setMaxValues(1);

        for (EpisodeDto ep : displayEpisodes) {
            String label = String.format("E%02d – %s",
                    ep.getEpisodeNumber() != null ? ep.getEpisodeNumber() : 0,
                    ep.getTitle() != null ? ep.getTitle() : "Unknown");
            if (label.length() > 100) label = label.substring(0, 97) + "...";

            String description = ep.isHasFile() ? "✅ Already downloaded" : "⬇️ Not yet downloaded";
            String value = String.valueOf(ep.getId());

            menuBuilder.addOption(label, value, description);
        }

        String prompt = String.format("**%s** — Season %d (%d episode%s):%s",
                seriesTitle, seasonNumber, displayEpisodes.size(),
                displayEpisodes.size() == 1 ? "" : "s",
                truncated ? "\n⚠️ Showing first 25 episodes only." : "");

        event.getHook().editOriginal(prompt)
                .setActionRow(menuBuilder.build())
                .queue();
    }

    /**
     * Handles the episode selection and triggers an EpisodeSearch command in Sonarr.
     * Component ID format: joenet:episodes:{sonarrSeriesId}
     */
    private void handleEpisodeSelection(StringSelectInteractionEvent event) {
        String selectId = event.getComponentId();
        // format: joenet:episodes:{sonarrSeriesId}
        String[] parts = selectId.split(":", 3);
        if (parts.length != 3) {
            event.reply("❌ Invalid selection.").setEphemeral(true).queue();
            return;
        }

        int episodeId;
        try {
            episodeId = Integer.parseInt(event.getValues().get(0));
        } catch (NumberFormatException e) {
            event.reply("❌ Invalid episode ID.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        boolean success = sonarrService.triggerEpisodeSearch(Collections.singletonList(episodeId));

        if (success) {
            event.getHook().editOriginal(
                    "✅ Episode search triggered! Sonarr will attempt to download the episode now."
            ).queue();
        } else {
            event.getHook().editOriginal(
                    "❌ Failed to trigger episode search. " +
                            "Please check your Sonarr connection or try again later."
            ).queue();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds the component ID for the "Specific Episode" button.
     * Truncates the title to ensure the total ID stays within Discord's 100-character limit.
     */
    String buildSpecificEpisodeButtonId(int tvdbId, String seriesTitle) {
        // "joenet:specificepisode:" + tvdbId (max 10 chars) + ":" = 34 chars prefix max
        // leaving 66 chars for the title
        return "joenet:specificepisode:" + tvdbId + ":" + truncateForId(seriesTitle);
    }

    /**
     * Truncates a string to 60 characters so it fits safely inside a component ID.
     */
    private String truncateForId(String value) {
        return value != null && value.length() > 60 ? value.substring(0, 60) : value;
    }

    /**
     * Posts a requester log message to the guild's configured requester log channel, if any.
     * Records who requested a given piece of media and when.
     *
     * @param guild the guild the request was made in
     * @param user  the user who made the request
     * @param title the title of the requested media
     */
    private void postRequesterLog(Guild guild, User user, String title) {
        if (guild == null) {
            return;
        }

        Long channelId = configDatabase.getRequesterLogChannelId(guild.getIdLong());
        if (channelId == null) {
            return;
        }

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Configured requester log channel {} not found in guild {}", channelId, guild.getIdLong());
            return;
        }

        String message = String.format("%s requested %s on %s.",
                user.getName(), title, formatOrdinalDate(LocalDateTime.now()));
        channel.sendMessage(message).queue();
    }

    /**
     * Formats a date as e.g. "March 14th 2024".
     */
    private static String formatOrdinalDate(LocalDateTime dateTime) {
        int day = dateTime.getDayOfMonth();
        String suffix;
        if (day >= 11 && day <= 13) {
            suffix = "th";
        } else {
            suffix = switch (day % 10) {
                case 1 -> "st";
                case 2 -> "nd";
                case 3 -> "rd";
                default -> "th";
            };
        }

        String month = dateTime.format(DateTimeFormatter.ofPattern("MMMM"));
        return String.format("%s %d%s %d", month, day, suffix, dateTime.getYear());
    }
}

