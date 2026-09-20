package org.fitznet.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.fitznet.dto.radarr.RadarrQueueItemDto;
import org.fitznet.dto.sonarr.SonarrQueueItemDto;
import org.fitznet.service.RadarrService;
import org.fitznet.service.SonarrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the /joenet status subcommand in JoenetCommands.
 */
class JoenetCommandsStatusTest {

    @Mock
    private RadarrService radarrService;

    @Mock
    private SonarrService sonarrService;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private User mockUser;

    @Mock
    private InteractionHook hook;

    @Mock
    private ReplyCallbackAction replyCallbackAction;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebhookMessageEditAction webhookMessageEditAction;

    private JoenetCommands joenetCommands;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MockitoAnnotations.openMocks(this);

        joenetCommands = new JoenetCommands();
        ReflectionTestUtils.setField(joenetCommands, "radarrService", radarrService);
        ReflectionTestUtils.setField(joenetCommands, "sonarrService", sonarrService);

        // Wire up JDA event mocks
        when(event.getName()).thenReturn("joenet");
        when(event.getSubcommandName()).thenReturn("status");
        when(event.isFromGuild()).thenReturn(true);
        when(event.isAcknowledged()).thenReturn(false);
        when(event.getUser()).thenReturn(mockUser);
        when(mockUser.getName()).thenReturn("TestUser");
        when(event.getGuild()).thenReturn(null);

        when(event.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        doNothing().when(replyCallbackAction).queue();

        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(webhookMessageEditAction);
        doNothing().when(webhookMessageEditAction).queue();
    }

    @Test
    void testStatusCommand_BothServicesSucceed() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Dune: Part Two", "downloading", 1_000_000.0, 400_000.0),
                createRadarrItem("Gladiator II", "queued", 800_000.0, 800_000.0)
        );
        List<SonarrQueueItemDto> sonarrItems = Arrays.asList(
                createSonarrItem("Breaking Bad S01E01", "downloading", 500_000.0, 100_000.0)
        );

        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenReturn(sonarrItems);

        joenetCommands.onSlashCommandInteraction(event);

        verify(radarrService, times(1)).getQueueDetails();
        verify(sonarrService, times(1)).getQueueDetails();
        verify(hook, times(1)).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    void testStatusCommand_RadarrFails_SonarrSucceeds() {
        List<SonarrQueueItemDto> sonarrItems = Arrays.asList(
                createSonarrItem("Better Call Saul S06E03", "queued", 400_000.0, 400_000.0)
        );

        when(radarrService.getQueueDetails()).thenThrow(new RuntimeException("Radarr connection refused"));
        when(sonarrService.getQueueDetails()).thenReturn(sonarrItems);

        joenetCommands.onSlashCommandInteraction(event);

        // Both services attempted, embed still sent with partial data
        verify(radarrService, times(1)).getQueueDetails();
        verify(sonarrService, times(1)).getQueueDetails();
        verify(hook, times(1)).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    void testStatusCommand_RadarrSucceeds_SonarrFails() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Oppenheimer", "completed", 2_000_000.0, 0.0)
        );

        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenThrow(new RuntimeException("Sonarr connection refused"));

        joenetCommands.onSlashCommandInteraction(event);

        verify(radarrService, times(1)).getQueueDetails();
        verify(sonarrService, times(1)).getQueueDetails();
        verify(hook, times(1)).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    void testStatusCommand_BothQueuesEmpty() {
        when(radarrService.getQueueDetails()).thenReturn(Collections.emptyList());
        when(sonarrService.getQueueDetails()).thenReturn(Collections.emptyList());

        joenetCommands.onSlashCommandInteraction(event);

        verify(radarrService, times(1)).getQueueDetails();
        verify(sonarrService, times(1)).getQueueDetails();
        verify(hook, times(1)).editOriginalEmbeds(any(MessageEmbed.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_CompletedItemsFilteredOut() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Completed Movie", "completed", 1_000_000.0, 0.0),
                createRadarrItem("Downloading Movie", "downloading", 1_000_000.0, 400_000.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenReturn(Collections.emptyList());

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        String radarrField = captor.getValue().getFields().get(0).getValue();
        assertThat(radarrField).doesNotContain("Completed Movie");
        assertThat(radarrField).contains("Downloading Movie");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_AllCompletedShowsQueueEmpty() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Completed Movie A", "completed", 1_000_000.0, 0.0),
                createRadarrItem("Completed Movie B", "COMPLETED", 800_000.0, 0.0)
        );
        List<SonarrQueueItemDto> sonarrItems = Arrays.asList(
                createSonarrItem("Completed Show S01E01", "Completed", 500_000.0, 0.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenReturn(sonarrItems);

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        MessageEmbed embed = captor.getValue();
        assertThat(embed.getFields().get(0).getValue()).isEqualTo("✅ Queue is empty");
        assertThat(embed.getFields().get(1).getValue()).isEqualTo("✅ Queue is empty");
        assertThat(embed.getFooter().getText()).isEqualTo("All caught up ✅");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_QueuedItemsCollapseToCount() {
        List<SonarrQueueItemDto> sonarrItems = Arrays.asList(
                createSonarrItem("Law & Order SVU S02E09", "queued", 400_000.0, 400_000.0),
                createSonarrItem("Law & Order SVU S02E03", "queued", 400_000.0, 400_000.0),
                createSonarrItem("Law & Order SVU S05E25", "queued", 400_000.0, 400_000.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(Collections.emptyList());
        when(sonarrService.getQueueDetails()).thenReturn(sonarrItems);

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        String sonarrField = captor.getValue().getFields().get(1).getValue();

        assertThat(sonarrField).contains("⏳ Queued — 3 titles waiting");
        assertThat(sonarrField).doesNotContain("Law & Order SVU S02E09");
        assertThat(sonarrField).doesNotContain("Law & Order SVU S02E03");
        assertThat(sonarrField).doesNotContain("Law & Order SVU S05E25");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_MultipleDownloadingItemsGroupedUnderHeader() {
        List<SonarrQueueItemDto> sonarrItems = Arrays.asList(
                createSonarrItem("Law & Order SVU S02E09", "downloading", 1_000_000.0, 380_000.0),
                createSonarrItem("Law & Order SVU S05E04", "downloading", 1_000_000.0, 820_000.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(Collections.emptyList());
        when(sonarrService.getQueueDetails()).thenReturn(sonarrItems);

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        String sonarrField = captor.getValue().getFields().get(1).getValue();

        assertThat(sonarrField).contains("Downloading (2)");
        assertThat(sonarrField).contains("Law & Order SVU S02E09");
        assertThat(sonarrField).contains("Law & Order SVU S05E04");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_SingleWarningItemShowsStalled() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Love Hurts 2025 1080p BluRay", "warning", 1_000_000.0, 1_000_000.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenReturn(Collections.emptyList());

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        String radarrField = captor.getValue().getFields().get(0).getValue();

        assertThat(radarrField).contains("Love Hurts 2025 1080p BluRay");
        assertThat(radarrField).contains("stalled at 0%");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_EmbedColorRedWhenFailedItemPresent() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Broken Download", "failed", 1_000_000.0, 1_000_000.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenReturn(Collections.emptyList());

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        assertThat(captor.getValue().getColorRaw() & 0xFFFFFF).isEqualTo(0xE74C3C);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_EmbedColorOrangeWhenOnlyWarningPresent() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Iffy Download", "warning", 1_000_000.0, 1_000_000.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenReturn(Collections.emptyList());

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        assertThat(captor.getValue().getColorRaw() & 0xFFFFFF).isEqualTo(0xE67E22);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_EmbedColorBlueWhenOnlyDownloadingOrQueued() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Normal Download", "downloading", 1_000_000.0, 400_000.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenReturn(Collections.emptyList());

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        assertThat(captor.getValue().getColorRaw() & 0xFFFFFF).isEqualTo(0x3498DB);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStatusCommand_FooterSummarizesCounts() {
        List<RadarrQueueItemDto> radarrItems = Arrays.asList(
                createRadarrItem("Warning Movie", "warning", 1_000_000.0, 1_000_000.0)
        );
        List<SonarrQueueItemDto> sonarrItems = Arrays.asList(
                createSonarrItem("Downloading Show", "downloading", 1_000_000.0, 400_000.0),
                createSonarrItem("Queued Show A", "queued", 400_000.0, 400_000.0),
                createSonarrItem("Queued Show B", "queued", 400_000.0, 400_000.0)
        );
        when(radarrService.getQueueDetails()).thenReturn(radarrItems);
        when(sonarrService.getQueueDetails()).thenReturn(sonarrItems);

        joenetCommands.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(captor.capture());
        assertThat(captor.getValue().getFooter().getText())
                .isEqualTo("1 warning · 1 downloading · 2 queued");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private RadarrQueueItemDto createRadarrItem(String title, String status, Double size, Double sizeleft) {
        RadarrQueueItemDto dto = new RadarrQueueItemDto();
        dto.setTitle(title);
        dto.setStatus(status);
        dto.setTrackedDownloadStatus("ok");
        dto.setSize(size);
        dto.setSizeleft(sizeleft);
        return dto;
    }

    private SonarrQueueItemDto createSonarrItem(String title, String status, Double size, Double sizeleft) {
        SonarrQueueItemDto dto = new SonarrQueueItemDto();
        dto.setTitle(title);
        dto.setStatus(status);
        dto.setTrackedDownloadStatus("ok");
        dto.setSize(size);
        dto.setSizeleft(sizeleft);
        return dto;
    }
}
