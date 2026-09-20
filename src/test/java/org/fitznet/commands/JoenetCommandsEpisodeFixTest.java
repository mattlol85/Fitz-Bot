package org.fitznet.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.fitznet.dto.sonarr.EpisodeDto;
import org.fitznet.dto.sonarr.SonarrSeriesDto;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the /joenet episodefix flow in {@link JoenetCommands}:
 * slash command -> button -> modal -> (series select) -> season select -> trigger search.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class JoenetCommandsEpisodeFixTest {

    @Mock private RadarrService radarrService;
    @Mock private SonarrService sonarrService;

    @Mock private SlashCommandInteractionEvent slashEvent;
    @Mock private ButtonInteractionEvent buttonEvent;
    @Mock private ModalInteractionEvent modalEvent;
    @Mock private ModalMapping searchTermMapping;
    @Mock private StringSelectInteractionEvent selectEvent;
    @Mock private User mockUser;
    @Mock private InteractionHook hook;
    @Mock private ReplyCallbackAction replyCallbackAction;
    @Mock private ModalCallbackAction modalCallbackAction;
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

        // Slash command defaults
        when(slashEvent.getName()).thenReturn("joenet");
        when(slashEvent.getSubcommandName()).thenReturn("episodefix");
        when(slashEvent.isFromGuild()).thenReturn(true);
        when(slashEvent.isAcknowledged()).thenReturn(false);
        when(slashEvent.getUser()).thenReturn(mockUser);
        when(slashEvent.getGuild()).thenReturn(null);
        when(slashEvent.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        when(slashEvent.getHook()).thenReturn(hook);

        // Button event defaults
        when(buttonEvent.getUser()).thenReturn(mockUser);
        when(buttonEvent.replyModal(any(Modal.class))).thenReturn(modalCallbackAction);
        doNothing().when(modalCallbackAction).queue();

        // Modal event defaults
        when(modalEvent.getUser()).thenReturn(mockUser);
        when(modalEvent.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        when(modalEvent.getHook()).thenReturn(hook);
        when(modalEvent.getValue("search-term")).thenReturn(searchTermMapping);

        // Select event defaults
        when(selectEvent.getUser()).thenReturn(mockUser);
        when(selectEvent.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        when(selectEvent.getHook()).thenReturn(hook);

        doNothing().when(replyCallbackAction).queue();
        when(hook.editOriginal(anyString())).thenReturn(webhookEditAction);
    }

    private SonarrSeriesDto series(int id, String title) {
        SonarrSeriesDto dto = new SonarrSeriesDto();
        dto.setId(id);
        dto.setTitle(title);
        return dto;
    }

    private EpisodeDto episode(int id, int season, int episodeNumber, boolean monitored, boolean hasFile) {
        EpisodeDto dto = new EpisodeDto();
        dto.setId(id);
        dto.setSeasonNumber(season);
        dto.setEpisodeNumber(episodeNumber);
        dto.setMonitored(monitored);
        dto.setHasFile(hasFile);
        return dto;
    }

    // ── handleEpisodeFixCommand ──────────────────────────────────────────────

    @Test
    void testEpisodeFixCommand_ShowsStartButton() {
        joenetCommands.onSlashCommandInteraction(slashEvent);

        verify(hook, times(1)).editOriginal(contains("Find a TV show"));
    }

    // ── handleEpisodeFixStartButton ──────────────────────────────────────────

    @Test
    void testEpisodeFixStartButton_OpensModal() {
        when(buttonEvent.getComponentId()).thenReturn("joenet:episodefix:start");

        joenetCommands.onButtonInteraction(buttonEvent);

        verify(buttonEvent, times(1)).replyModal(any(Modal.class));
        verify(modalCallbackAction, times(1)).queue();
    }

    // ── handleEpisodeFixSearchModal ──────────────────────────────────────────

    @Test
    void testEpisodeFixSearchModal_NoLibraryMatch_ShowsError() {
        when(modalEvent.getModalId()).thenReturn("joenet:episodefix:search");
        when(searchTermMapping.getAsString()).thenReturn("Nonexistent Show");
        when(sonarrService.searchLibrarySeries("Nonexistent Show")).thenReturn(Collections.emptyList());

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("No matching show found"));
        verify(sonarrService, never()).getEpisodes(anyInt());
    }

    @Test
    void testEpisodeFixSearchModal_SingleMatch_NoMissingEpisodes_ShowsAllClear() {
        when(modalEvent.getModalId()).thenReturn("joenet:episodefix:search");
        when(searchTermMapping.getAsString()).thenReturn("The Office");
        when(sonarrService.searchLibrarySeries("The Office"))
                .thenReturn(Collections.singletonList(series(8, "The Office")));
        when(sonarrService.getEpisodes(8)).thenReturn(Arrays.asList(
                episode(1, 1, 1, true, true),
                episode(2, 1, 2, true, true)
        ));

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("no missing episodes"));
    }

    @Test
    void testEpisodeFixSearchModal_SingleMatch_MissingEpisodes_ShowsSeasonPicker() {
        when(modalEvent.getModalId()).thenReturn("joenet:episodefix:search");
        when(searchTermMapping.getAsString()).thenReturn("Always Sunny");
        when(sonarrService.searchLibrarySeries("Always Sunny"))
                .thenReturn(Collections.singletonList(series(7, "It's Always Sunny in Philadelphia")));
        when(sonarrService.getEpisodes(7)).thenReturn(Arrays.asList(
                episode(101, 6, 8, true, true),
                episode(102, 6, 9, true, false),
                episode(103, 6, 10, true, false),
                episode(104, 1, 1, true, true)
        ));

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("missing episodes in 1 season"));
    }

    @Test
    void testEpisodeFixSearchModal_MultipleMatches_ShowsSelectMenu() {
        when(modalEvent.getModalId()).thenReturn("joenet:episodefix:search");
        when(searchTermMapping.getAsString()).thenReturn("Office");
        when(sonarrService.searchLibrarySeries("Office")).thenReturn(Arrays.asList(
                series(8, "The Office (US)"),
                series(9, "The Office (UK)")
        ));

        joenetCommands.onModalInteraction(modalEvent);

        verify(hook, times(1)).editOriginal(contains("Found 2 show(s)"));
        verify(sonarrService, never()).getEpisodes(anyInt());
    }

    // ── handleEpisodeFixSeriesSelection ──────────────────────────────────────

    @Test
    void testEpisodeFixSeriesSelection_MissingEpisodes_ShowsSeasonPicker() {
        when(selectEvent.getComponentId()).thenReturn("joenet:episodefix:select");
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("8:The Office (US)"));
        when(sonarrService.getEpisodes(8)).thenReturn(Arrays.asList(
                episode(1, 3, 1, true, false)
        ));

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(sonarrService, times(1)).getEpisodes(8);
        verify(hook, times(1)).editOriginal(contains("missing episodes in 1 season"));
    }

    // ── handleEpisodeFixSeasonSelection ──────────────────────────────────────

    @Test
    void testEpisodeFixSeasonSelection_TriggersSearchForMissingOnly() {
        String selectId = "joenet:episodefix:seasons:7:It's Always Sunny in Philadelphia";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("6"));
        when(sonarrService.getEpisodes(7)).thenReturn(Arrays.asList(
                episode(101, 6, 8, true, true),   // has file — should NOT be searched
                episode(102, 6, 9, true, false),  // missing — should be searched
                episode(103, 6, 10, true, false), // missing — should be searched
                episode(104, 1, 1, true, false)   // missing but different season — should NOT be searched
        ));
        when(sonarrService.triggerEpisodeSearch(anyList())).thenReturn(true);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(sonarrService, times(1)).triggerEpisodeSearch(Arrays.asList(102, 103));
        verify(hook, times(1)).editOriginal(contains("Triggered a search for 2 missing episode(s)"));
    }

    @Test
    void testEpisodeFixSeasonSelection_All_TargetsEverySeasonWithGaps() {
        String selectId = "joenet:episodefix:seasons:7:It's Always Sunny in Philadelphia";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("all"));
        when(sonarrService.getEpisodes(7)).thenReturn(Arrays.asList(
                episode(102, 6, 9, true, false),
                episode(104, 1, 1, true, false)
        ));
        when(sonarrService.triggerEpisodeSearch(anyList())).thenReturn(true);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(sonarrService, times(1)).triggerEpisodeSearch(Arrays.asList(102, 104));
        verify(hook, times(1)).editOriginal(contains("2 season(s)"));
    }

    @Test
    void testEpisodeFixSeasonSelection_SonarrFailure_ShowsError() {
        String selectId = "joenet:episodefix:seasons:7:Always Sunny";
        when(selectEvent.getComponentId()).thenReturn(selectId);
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("6"));
        when(sonarrService.getEpisodes(7)).thenReturn(Collections.singletonList(
                episode(102, 6, 9, true, false)
        ));
        when(sonarrService.triggerEpisodeSearch(anyList())).thenReturn(false);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(hook, times(1)).editOriginal(contains("❌ Failed to trigger search"));
    }

    @Test
    void testEpisodeFixSeasonSelection_InvalidSeriesId_ReturnsError() {
        when(selectEvent.getComponentId()).thenReturn("joenet:episodefix:seasons:notanumber:Some Show");
        when(selectEvent.reply(anyString())).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(anyBoolean())).thenReturn(replyCallbackAction);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(selectEvent, times(1)).reply(contains("❌ Invalid show ID"));
    }
}
