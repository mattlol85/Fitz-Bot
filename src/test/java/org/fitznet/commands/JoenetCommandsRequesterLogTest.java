package org.fitznet.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.fitznet.data.GuildConfigDatabase;
import org.fitznet.service.RadarrService;
import org.fitznet.service.SonarrService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the requester log feature of the /joenet download flow.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class JoenetCommandsRequesterLogTest {

    private static final long TEST_GUILD_ID = 123456789L;
    private static final String TEST_USER_NAME = "Joseph";

    @Mock private RadarrService radarrService;
    @Mock private SonarrService sonarrService;

    @Mock private StringSelectInteractionEvent selectEvent;
    @Mock private User mockUser;
    @Mock private Guild mockGuild;
    @Mock private InteractionHook hook;
    @Mock private ReplyCallbackAction replyCallbackAction;
    @Mock(answer = Answers.RETURNS_SELF)
    private WebhookMessageEditAction webhookEditAction;
    @Mock private TextChannel mockChannel;
    @Mock private MessageCreateAction messageCreateAction;

    private GuildConfigDatabase configDatabase;
    private JoenetCommands joenetCommands;
    private String configPath;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        configPath = tempDir.resolve("guild_configs_test.json").toString();
        configDatabase = new GuildConfigDatabase(configPath);

        joenetCommands = new JoenetCommands();
        ReflectionTestUtils.setField(joenetCommands, "radarrService", radarrService);
        ReflectionTestUtils.setField(joenetCommands, "sonarrService", sonarrService);
        ReflectionTestUtils.setField(joenetCommands, "configDatabase", configDatabase);

        when(mockUser.getName()).thenReturn(TEST_USER_NAME);
        when(mockGuild.getIdLong()).thenReturn(TEST_GUILD_ID);

        when(selectEvent.getUser()).thenReturn(mockUser);
        when(selectEvent.getGuild()).thenReturn(mockGuild);
        when(selectEvent.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        doNothing().when(replyCallbackAction).queue();
        when(selectEvent.getHook()).thenReturn(hook);
        when(hook.editOriginal(anyString())).thenReturn(webhookEditAction);

        when(mockChannel.sendMessage(anyString())).thenReturn(messageCreateAction);
        doNothing().when(messageCreateAction).queue();
    }

    @AfterEach
    void tearDown() {
        File configFile = new File(configPath);
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    void testMovieDownload_PostsRequesterLog_WhenChannelConfigured() {
        long logChannelId = 987654321L;
        configDatabase.setRequesterLogChannelId(TEST_GUILD_ID, logChannelId);
        when(mockGuild.getTextChannelById(logChannelId)).thenReturn(mockChannel);

        when(selectEvent.getComponentId()).thenReturn("joenet:select:movie");
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("603:The Matrix"));
        when(radarrService.downloadMovie(603, "The Matrix")).thenReturn(true);

        joenetCommands.onStringSelectInteraction(selectEvent);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockChannel).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.startsWith(TEST_USER_NAME + " requested The Matrix on "),
                "Expected requester log message, got: " + message);
    }

    @Test
    void testMovieDownload_NoRequesterLog_WhenChannelNotConfigured() {
        when(selectEvent.getComponentId()).thenReturn("joenet:select:movie");
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("603:The Matrix"));
        when(radarrService.downloadMovie(603, "The Matrix")).thenReturn(true);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(mockChannel, never()).sendMessage(anyString());
    }

    @Test
    void testMovieDownload_NoRequesterLog_WhenDownloadFails() {
        long logChannelId = 987654321L;
        configDatabase.setRequesterLogChannelId(TEST_GUILD_ID, logChannelId);

        when(selectEvent.getComponentId()).thenReturn("joenet:select:movie");
        when(selectEvent.getValues()).thenReturn(Collections.singletonList("603:The Matrix"));
        when(radarrService.downloadMovie(603, "The Matrix")).thenReturn(false);

        joenetCommands.onStringSelectInteraction(selectEvent);

        verify(mockChannel, never()).sendMessage(anyString());
    }
}
