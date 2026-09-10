package org.fitznet.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.fitznet.dto.sonarr.Season;
import org.fitznet.dto.sonarr.SeriesSearchResponseDto;
import org.fitznet.service.DownloadResult;
import org.fitznet.service.RadarrService;
import org.fitznet.service.SonarrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the movie/season selection handlers in {@link JoenetCommands},
 * covering the {@link DownloadResult} tri-state responses.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class JoenetCommandsSelectionTest {

    @Mock private RadarrService radarrService;
    @Mock private SonarrService sonarrService;

    @Mock private StringSelectInteractionEvent selectEvent;
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
        when(selectEvent.getUser()).thenReturn(mockUser);
        when(selectEvent.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        doNothing().when(replyCallbackAction).queue();
        when(selectEvent.getHook()).thenReturn(hook);
        when(hook.editOriginal(anyString())).thenReturn(webhookEditAction);
    }

    @Test
    void testMovieSelection_Added_ShowsSuccess() {
        when(selectEvent.getComponentId()).thenReturn("joenet:select:movie");
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("438631:Dune"));
        when(radarrService.downloadMovie(eq(438631), anyString())).thenReturn(DownloadResult.ADDED);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(hook, times(1)).editOriginal(contains("Successfully added"));
    }

    @Test
    void testMovieSelection_AlreadyExists_ShowsAlreadyInLibrary() {
        when(selectEvent.getComponentId()).thenReturn("joenet:select:movie");
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("515295:Kase-san"));
        when(radarrService.downloadMovie(anyInt(), anyString())).thenReturn(DownloadResult.ALREADY_EXISTS);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(hook, times(1)).editOriginal(contains("already in your library"));
        verify(hook, never()).editOriginal(contains("❌"));
    }

    @Test
    void testMovieSelection_Failed_ShowsFailure() {
        when(selectEvent.getComponentId()).thenReturn("joenet:select:movie");
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("1:Whatever"));
        when(radarrService.downloadMovie(anyInt(), anyString())).thenReturn(DownloadResult.FAILED);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(hook, times(1)).editOriginal(contains("❌"));
    }

    @Test
    void testSeasonSelection_AlreadyExists_ShowsAlreadyInLibrary() {
        when(selectEvent.getComponentId()).thenReturn("joenet:seasons:81189:Breaking Bad");
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("all"));

        SeriesSearchResponseDto series = new SeriesSearchResponseDto();
        series.setTitle("Breaking Bad");
        series.setTvdbId(81189);
        series.setSeasons(Arrays.asList(new Season(1, false), new Season(2, false)));
        when(sonarrService.searchSeries("Breaking Bad")).thenReturn(List.of(series));
        when(sonarrService.downloadSeries(eq(81189), anyString(), anyList()))
                .thenReturn(DownloadResult.ALREADY_EXISTS);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(hook, times(1)).editOriginal(contains("already in your library"));
    }
}
