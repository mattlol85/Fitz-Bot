package org.fitznet.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.fitznet.dto.radarr.MovieSearchResponseDto;
import org.fitznet.dto.sonarr.SeriesSearchResponseDto;
import org.fitznet.service.MediaSearchException;
import org.fitznet.service.RadarrService;
import org.fitznet.service.SonarrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the movie/TV search modal flow in {@link JoenetCommands}.
 * Regression coverage for over-long select-menu options and reply-after-defer.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class JoenetCommandsModalTest {

    @Mock private RadarrService radarrService;
    @Mock private SonarrService sonarrService;

    @Mock private ModalInteractionEvent modalEvent;
    @Mock private ModalMapping searchTermMapping;
    @Mock private User mockUser;
    @Mock private InteractionHook hook;
    @Mock private ReplyCallbackAction replyCallbackAction;
    @Mock(answer = Answers.RETURNS_SELF)
    private WebhookMessageEditAction webhookEditAction;

    private JoenetCommands joenetCommands;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        joenetCommands = new JoenetCommands();
        ReflectionTestUtils.setField(joenetCommands, "radarrService", radarrService);
        ReflectionTestUtils.setField(joenetCommands, "sonarrService", sonarrService);

        when(mockUser.getName()).thenReturn("TestUser");
        when(modalEvent.getUser()).thenReturn(mockUser);
        when(modalEvent.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        doNothing().when(replyCallbackAction).queue();
        when(modalEvent.getHook()).thenReturn(hook);
        when(modalEvent.isAcknowledged()).thenReturn(true);
        when(modalEvent.getValue("search-term")).thenReturn(searchTermMapping);
        when(hook.editOriginal(anyString())).thenReturn(webhookEditAction);
    }

    private MovieSearchResponseDto movie(String title, Integer year, int tmdbId) {
        MovieSearchResponseDto m = new MovieSearchResponseDto();
        m.setTitle(title);
        m.setYear(year);
        m.setTmdbId(tmdbId);
        return m;
    }

    @Test
    void testMovieSearchModal_LongTitleMovie_BuildsMenuWithoutThrowing() {
        when(modalEvent.getModalId()).thenReturn("joenet:search:movie");
        when(searchTermMapping.getAsString()).thenReturn("borat");

        String longTitle = "Borat Subsequent Moviefilm: Delivery of Prodigious Bribe to American "
                + "Regime for Make Benefit Once Glorious Nation of Kazakhstan";
        when(radarrService.searchMovies("borat"))
                .thenReturn(List.of(movie(longTitle, 2020, 515295)));

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("Found 1 movie"));
        verify(modalEvent, never()).reply(anyString());
    }

    @Test
    void testMovieSearchModal_NoResults_ShowsNotFound() {
        when(modalEvent.getModalId()).thenReturn("joenet:search:movie");
        when(searchTermMapping.getAsString()).thenReturn("zzz");
        when(radarrService.searchMovies("zzz")).thenReturn(Collections.emptyList());

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("No movies found"));
    }

    @Test
    void testMovieSearchModal_MetadataUnavailable_ShowsWarning() {
        when(modalEvent.getModalId()).thenReturn("joenet:search:movie");
        when(searchTermMapping.getAsString()).thenReturn("dune");
        when(radarrService.searchMovies("dune")).thenThrow(new MediaSearchException("down", null));

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("temporarily unavailable"));
        verify(modalEvent, never()).reply(anyString());
    }

    @Test
    void testTvSearchModal_MetadataUnavailable_ShowsWarning() {
        when(modalEvent.getModalId()).thenReturn("joenet:search:tv");
        when(searchTermMapping.getAsString()).thenReturn("kase-san");
        when(sonarrService.searchSeries("kase-san")).thenThrow(new MediaSearchException("down", null));

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("temporarily unavailable"));
        verify(modalEvent, never()).reply(anyString());
    }

    @Test
    void testModalInteraction_HandlerThrowsAfterDefer_UsesHookNotReply() {
        when(modalEvent.getModalId()).thenReturn("joenet:search:movie");
        when(searchTermMapping.getAsString()).thenReturn("boom");
        when(radarrService.searchMovies("boom")).thenThrow(new RuntimeException("kaboom"));

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("❌"));
        verify(modalEvent, never()).reply(anyString());
    }

    @Test
    void testTvSearchModal_LongTitleSeries_BuildsMenuWithoutThrowing() {
        when(modalEvent.getModalId()).thenReturn("joenet:search:tv");
        when(searchTermMapping.getAsString()).thenReturn("long");

        SeriesSearchResponseDto series = new SeriesSearchResponseDto();
        series.setTitle("A".repeat(140));
        series.setYear(2024);
        series.setTvdbId(999999);
        when(sonarrService.searchSeries("long")).thenReturn(List.of(series));

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("Found 1 TV show"));
        verify(modalEvent, never()).reply(anyString());
    }
}
