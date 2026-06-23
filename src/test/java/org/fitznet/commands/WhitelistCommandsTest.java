package org.fitznet.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.fitznet.data.GuildConfigDatabase;
import org.fitznet.service.MinecraftRconService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WhitelistCommandsTest {

    @Mock private SlashCommandInteractionEvent event;
    @Mock private Guild guild;
    @Mock private Member member;
    @Mock private User user;
    @Mock private InteractionHook hook;
    @Mock private WebhookMessageEditAction<Message> editAction;
    @Mock private MinecraftRconService rconService;

    private GuildConfigDatabase database;
    private WhitelistCommands commands;

    private static final long GUILD_ID = 999L;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        database = new GuildConfigDatabase(tempDir.resolve("configs.json").toString());
        commands = new WhitelistCommands(database, rconService);

        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getUser()).thenReturn(user);
        when(event.getHook()).thenReturn(hook);
        when(event.isFromGuild()).thenReturn(true);
        when(event.isAcknowledged()).thenReturn(true);
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(guild.getName()).thenReturn("Test Guild");
        when(user.getName()).thenReturn("tester");
        when(hook.editOriginal(anyString())).thenReturn(editAction);
        doNothing().when(editAction).queue();
    }

    private void mockUsernameOption(String username) {
        OptionMapping option = mock(OptionMapping.class);
        when(option.getAsString()).thenReturn(username);
        when(event.getOption("username")).thenReturn(option);
    }

    @Test
    void getCommandsExposesWhitelistAndSetRole() {
        SlashCommandData[] cmds = WhitelistCommands.getCommands();
        assertEquals(2, cmds.length);
        List<String> names = List.of(cmds[0].getName(), cmds[1].getName());
        assertTrue(names.contains("whitelist"));
        assertTrue(names.contains("setwhitelistrole"));
    }

    @Test
    void deniesMemberWithoutRoleOrAdmin() {
        when(event.getName()).thenReturn("whitelist");
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);
        when(member.getRoles()).thenReturn(List.of());
        mockUsernameOption("Steve");

        commands.onSlashCommandInteraction(event);

        verify(hook).editOriginal(contains("don't have permission"));
        verifyNoInteractions(rconService);
    }

    @Test
    void adminCanWhitelistAndSeesServerResponse() throws Exception {
        when(event.getName()).thenReturn("whitelist");
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(member.getEffectiveName()).thenReturn("Admin");
        mockUsernameOption("Steve");
        when(rconService.addToWhitelist("Steve")).thenReturn("Added Steve to the whitelist");

        commands.onSlashCommandInteraction(event);

        verify(rconService).addToWhitelist("Steve");
        verify(hook).editOriginal(contains("Added Steve to the whitelist"));
    }

    @Test
    void memberWithConfiguredRoleIsAllowed() throws Exception {
        long roleId = 555L;
        database.setWhitelistRoleId(GUILD_ID, roleId);
        Role role = mock(Role.class);
        when(role.getIdLong()).thenReturn(roleId);

        when(event.getName()).thenReturn("whitelist");
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);
        when(member.getRoles()).thenReturn(List.of(role));
        when(member.getEffectiveName()).thenReturn("Trusted");
        mockUsernameOption("Notch");
        when(rconService.addToWhitelist("Notch")).thenReturn("Added Notch to the whitelist");

        commands.onSlashCommandInteraction(event);

        verify(rconService).addToWhitelist("Notch");
        verify(hook).editOriginal(contains("Added Notch to the whitelist"));
    }

    @Test
    void rejectsInvalidUsernameWithoutCallingRcon() throws Exception {
        when(event.getName()).thenReturn("whitelist");
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        mockUsernameOption("bad name!");

        commands.onSlashCommandInteraction(event);

        verify(hook).editOriginal(contains("valid Minecraft username"));
        verify(rconService, never()).addToWhitelist(anyString());
    }
}
