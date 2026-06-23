package org.fitznet.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.fitznet.data.GuildConfigDatabase;
import org.fitznet.service.MinecraftRconService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Slash command handler for managing the Minecraft server whitelist.
 *
 * <p>{@code /whitelist} is gated to members holding a configurable role (set via
 * {@code /setwhitelistrole}); administrators are always allowed. Combined with
 * {@code white-list=true} and {@code online-mode=true} on the server, this keeps
 * anonymous crawlers off the Minecraft server.</p>
 */
@Slf4j
@Component
public class WhitelistCommands extends ListenerAdapter {

    static final String WHITELIST = "whitelist";
    static final String SET_ROLE = "setwhitelistrole";

    private final GuildConfigDatabase configDatabase;
    private final MinecraftRconService rconService;

    @Autowired
    public WhitelistCommands(MinecraftRconService rconService) {
        this(new GuildConfigDatabase(), rconService);
    }

    /** Package-private constructor for testing with an injected database. */
    WhitelistCommands(GuildConfigDatabase configDatabase, MinecraftRconService rconService) {
        this.configDatabase = configDatabase;
        this.rconService = rconService;
    }

    /**
     * Gets the slash command definitions.
     */
    public static SlashCommandData[] getCommands() {
        return new SlashCommandData[]{
                Commands.slash(WHITELIST, "Add a player to the Minecraft whitelist")
                        .addOption(OptionType.STRING, "username", "The Minecraft username to whitelist", true),
                Commands.slash(SET_ROLE, "Set the Discord role allowed to use /whitelist (Admin only)")
                        .addOption(OptionType.ROLE, "role", "The role that may add players to the whitelist", true)
        };
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String name = event.getName();
        if (!WHITELIST.equals(name) && !SET_ROLE.equals(name)) {
            return; // Let other handlers process this command
        }

        log.info("Received slash command: {} from user: {} in guild: {}",
                name, event.getUser().getName(),
                event.getGuild() != null ? event.getGuild().getName() : "DM");

        try {
            if (!event.isAcknowledged()) {
                event.deferReply(true).queue();
            }

            if (!event.isFromGuild()) {
                event.getHook().editOriginal("❌ This command can only be used in a server!").queue();
                return;
            }

            switch (name) {
                case SET_ROLE -> handleSetRole(event);
                case WHITELIST -> handleWhitelist(event);
                default -> event.getHook().editOriginal("❌ Unknown command").queue();
            }
        } catch (Exception e) {
            log.error("Error in onSlashCommandInteraction for command: {}", name, e);
            replyError(event);
        }
    }

    private void handleSetRole(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.getHook().editOriginal("❌ You need the 'Manage Server' permission to use this command!").queue();
            return;
        }

        Role role = event.getOption("role").getAsRole();
        configDatabase.setWhitelistRoleId(event.getGuild().getIdLong(), role.getIdLong());
        event.getHook().editOriginal("✅ " + role.getAsMention() + " can now use `/whitelist`.").queue();
    }

    private void handleWhitelist(SlashCommandInteractionEvent event) {
        if (!isAllowed(event)) {
            event.getHook().editOriginal(
                    "❌ You don't have permission to use `/whitelist`. Ask an admin to grant you the whitelist role.").queue();
            return;
        }

        String username = event.getOption("username").getAsString().trim();
        if (!MinecraftRconService.isValidUsername(username)) {
            event.getHook().editOriginal(
                    "❌ That doesn't look like a valid Minecraft username (3-16 letters, digits, or underscores).").queue();
            return;
        }

        try {
            String response = rconService.addToWhitelist(username);
            String message = (response == null || response.isBlank())
                    ? "✅ Sent `whitelist add " + username + "` to the server."
                    : "✅ " + response.trim();
            event.getHook().editOriginal(message).queue();
            log.info("User {} whitelisted '{}' in guild {}",
                    event.getMember().getEffectiveName(), username, event.getGuild().getName());
        } catch (IllegalArgumentException e) {
            event.getHook().editOriginal("❌ That doesn't look like a valid Minecraft username.").queue();
        } catch (IOException e) {
            log.error("Failed to whitelist {} via RCON", username, e);
            event.getHook().editOriginal("❌ Couldn't reach the Minecraft server. Please try again later.").queue();
        }
    }

    /**
     * A member may use {@code /whitelist} if they are an administrator or hold the
     * configured whitelist role. If no role is configured, only admins are allowed.
     */
    private boolean isAllowed(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        Long roleId = configDatabase.getWhitelistRoleId(event.getGuild().getIdLong());
        if (roleId == null) {
            return false;
        }
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == roleId);
    }

    private void replyError(SlashCommandInteractionEvent event) {
        try {
            if (event.isAcknowledged()) {
                event.getHook().editOriginal("❌ An error occurred while processing your command. Please try again.").queue();
            } else {
                event.reply("❌ An error occurred while processing your command. Please try again.")
                        .setEphemeral(true).queue();
            }
        } catch (Exception replyError) {
            log.error("Failed to send error response", replyError);
        }
    }
}
