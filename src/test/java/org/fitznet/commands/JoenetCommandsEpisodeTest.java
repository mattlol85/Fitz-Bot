package org.fitznet.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.mockito.Answers;
import org.fitznet.dto.sonarr.EpisodeDto;
import org.fitznet.dto.sonarr.Season;
import org.fitznet.dto.sonarr.SeriesSearchResponseDto;
import org.fitznet.dto.sonarr.SonarrSeriesDto;
import org.fitznet.service.RadarrService;
import org.fitznet.service.SonarrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the specific-episode download flow in JoenetCommands.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class JoenetCommandsEpisodeTest {

    @Mock private RadarrService radarrService;
    @Mock private SonarrService sonarrService;

    @Mock private ButtonInteractionEvent buttonEvent;
    @Mock private StringSelectInteractionEvent selectEvent;
    @Mock private User mockUser;
    @Mock private InteractionHook hook;
    @Mock private ReplyCallbackAction replyCallbackAction;
    @Mock(answer = Answers.RETURNS_SELF)
    @SuppressWarnings("rawtypes")
    private WebhookMessageEditAction webhookEditAction;

    private JoenetCommands joenetCommands;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        joenetCommands = new JoenetCommands();
        ReflectionTestUtils.setField(joenetCommands, "radarrService", radarrService);
        ReflectionTestUtils.setField(joenetCommands, "sonarrService", sonarrService);

        when(mockUser.getName()).thenReturn("TestUser");

        // Button event defaults
        when(buttonEvent.getUser()).thenReturn(mockUser);
        when(buttonEvent.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        doNothing().when(replyCallbackAction).queue();
        when(buttonEvent.getHook()).thenReturn(hook);
        when(hook.editOriginal(anyString())).thenReturn(webhookEditAction);
        // webhookEditAction uses RETURNS_SELF so the fluent builder chain resolves automatically

        // Select event defaults
        when(selectEvent.getUser()).thenReturn(mockUser);
        when(selectEvent.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        when(selectEvent.getHook()).thenReturn(hook);
    }

    // ── buildSpecificEpisodeButtonId ─────────────────────────────────────────

    @Test
    void testBuildSpecificEpisodeButtonId_ShortTitle() {
        String id = joenetCommands.buildSpecificEpisodeButtonId(12345, "The Boys");
        assertTrue(id.startsWith("joenet:specificepisode:12345:"));
        assertTrue(id.contains("The Boys"));
        assertTrue(id.length() <= 100);
    }

    @Test
    void testBuildSpecificEpisodeButtonId_LongTitle_TruncatesTo100Chars() {
        String longTitle = "A".repeat(80);
        String id = joenetCommands.buildSpecificEpisodeButtonId(9999999, longTitle);
        assertTrue(id.length() <= 100,
                "Component ID must not exceed 100 chars, got " + id.length());
    }

    // ── handleSpecificEpisodeButton ──────────────────────────────────────────

    @Test
    void testSpecificEpisodeButton_ShowsSeasonPicker() {
        String buttonId = "joenet:specificepisode:305288:The Boys";
        when(buttonEvent.getComponentId()).thenReturn(buttonId);

        SeriesSearchResponseDto series = buildSeries(305288, "The Boys", 5);
        when(sonarrService.searchSeries("The Boys")).thenReturn(Collections.singletonList(series));

        joenetCommands.onButtonInteraction(buttonEvent);

        verify(sonarrService, times(1)).searchSeries("The Boys");
        verify(hook, times(1)).editOriginal(contains("The Boys"));
    }

    @Test
    void testSpecificEpisodeButton_InvalidId_ReturnsError() {
        when(buttonEvent.getComponentId()).thenReturn("joenet:specificepisode:notanumber:The Boys");
        when(buttonEvent.reply(anyString())).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(anyBoolean())).thenReturn(replyCallbackAction);

        joenetCommands.onButtonInteraction(buttonEvent);

        verify(buttonEvent, times(1)).reply(contains("❌"));
    }

    @Test
    void testSpecificEpisodeButton_NoSeasons_ReturnsError() {
        String buttonId = "joenet:specificepisode:305288:The Boys";
        when(buttonEvent.getComponentId()).thenReturn(buttonId);
        when(sonarrService.searchSeries("The Boys")).thenReturn(Collections.emptyList());

        joenetCommands.onButtonInteraction(buttonEvent);

        verify(hook, times(1)).editOriginal(contains("❌"));
    }

    // ── handleEpisodeSeasonSelection ─────────────────────────────────────────

    @Test
    void testEpisodeSeasonSelection_SeriesNotInLibrary_ShowsHelpMessage() {
        String selectId = "joenet:episodeseason:305288:The Boys";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("5"));
        when(sonarrService.getSeriesByTvdbId(305288)).thenReturn(null);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(sonarrService, times(1)).getSeriesByTvdbId(305288);
        verify(hook, times(1)).editOriginal(contains("not in your Sonarr library"));
    }

    @Test
    void testEpisodeSeasonSelection_SeriesInLibrary_ShowsEpisodes() {
        String selectId = "joenet:episodeseason:305288:The Boys";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("5"));

        SonarrSeriesDto librarySeries = new SonarrSeriesDto();
        librarySeries.setId(42);
        librarySeries.setTvdbId(305288);
        librarySeries.setTitle("The Boys");
        when(sonarrService.getSeriesByTvdbId(305288)).thenReturn(librarySeries);

        List<EpisodeDto> episodes = Arrays.asList(
                buildEpisode(201, 5, 7, "Episode 7", false),
                buildEpisode(202, 5, 8, "Episode 8", true)
        );
        when(sonarrService.getEpisodes(42, 5)).thenReturn(episodes);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(sonarrService, times(1)).getEpisodes(42, 5);
        verify(hook, times(1)).editOriginal(contains("Season 5"));
    }

    @Test
    void testEpisodeSeasonSelection_NoEpisodes_ShowsError() {
        String selectId = "joenet:episodeseason:305288:The Boys";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("99"));

        SonarrSeriesDto librarySeries = new SonarrSeriesDto();
        librarySeries.setId(42);
        when(sonarrService.getSeriesByTvdbId(305288)).thenReturn(librarySeries);
        when(sonarrService.getEpisodes(42, 99)).thenReturn(Collections.emptyList());

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(hook, times(1)).editOriginal(contains("❌"));
    }

    // ── handleEpisodeSelection ───────────────────────────────────────────────

    @Test
    void testEpisodeSelection_Success() {
        String selectId = "joenet:episodes:42";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("201"));
        when(sonarrService.triggerEpisodeSearch(Collections.singletonList(201))).thenReturn(true);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(sonarrService, times(1)).triggerEpisodeSearch(Collections.singletonList(201));
        verify(hook, times(1)).editOriginal(contains("✅"));
    }

    @Test
    void testEpisodeSelection_Failure() {
        String selectId = "joenet:episodes:42";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("201"));
        when(sonarrService.triggerEpisodeSearch(Collections.singletonList(201))).thenReturn(false);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(sonarrService, times(1)).triggerEpisodeSearch(Collections.singletonList(201));
        verify(hook, times(1)).editOriginal(contains("❌"));
    }

    @Test
    void testEpisodeSelection_InvalidEpisodeId_ReturnsError() {
        String selectId = "joenet:episodes:42";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("not-a-number"));
        when(selectEvent.reply(anyString())).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(anyBoolean())).thenReturn(replyCallbackAction);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(selectEvent, times(1)).reply(contains("❌ Invalid episode ID"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private SeriesSearchResponseDto buildSeries(int tvdbId, String title, int numSeasons) {
        SeriesSearchResponseDto series = new SeriesSearchResponseDto();
        series.setTitle(title);
        series.setTvdbId(tvdbId);
        series.setYear(2019);
        List<Season> seasons = new ArrayList<>();
        for (int i = 1; i <= numSeasons; i++) {
            seasons.add(new Season(i, false));
        }
        series.setSeasons(seasons);
        return series;
    }

    private EpisodeDto buildEpisode(int id, int season, int episode, String title, boolean hasFile) {
        EpisodeDto dto = new EpisodeDto();
        dto.setId(id);
        dto.setSeasonNumber(season);
        dto.setEpisodeNumber(episode);
        dto.setTitle(title);
        dto.setHasFile(hasFile);
        return dto;
    }
}






