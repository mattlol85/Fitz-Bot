package org.fitznet.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftRconServiceTest {

    @Test
    void acceptsValidUsernames() {
        assertTrue(MinecraftRconService.isValidUsername("Steve"));
        assertTrue(MinecraftRconService.isValidUsername("Notch_99"));
        assertTrue(MinecraftRconService.isValidUsername("abc"));              // 3 chars (minimum)
        assertTrue(MinecraftRconService.isValidUsername("A1234567890_abcd")); // 16 chars (maximum)
    }

    @Test
    void rejectsInvalidUsernames() {
        assertFalse(MinecraftRconService.isValidUsername(null));
        assertFalse(MinecraftRconService.isValidUsername(""));
        assertFalse(MinecraftRconService.isValidUsername("ab"));                // too short
        assertFalse(MinecraftRconService.isValidUsername("ThisNameIsTooLong")); // 17 chars
        assertFalse(MinecraftRconService.isValidUsername("has space"));
        assertFalse(MinecraftRconService.isValidUsername("dash-name"));
    }

    @Test
    void rejectsRconInjectionAttempts() {
        // Spaces / newlines / separators must never reach the server, where they
        // could be interpreted as additional RCON commands.
        assertFalse(MinecraftRconService.isValidUsername("Steve op @a"));
        assertFalse(MinecraftRconService.isValidUsername("Steve\nop"));
        assertFalse(MinecraftRconService.isValidUsername("Steve;stop"));
    }

    @Test
    void addToWhitelistRejectsInvalidUsernameWithoutConnecting() {
        MinecraftRconService service = new MinecraftRconService();
        // No RCON host configured; an invalid username must fail before any socket is opened.
        assertThrows(IllegalArgumentException.class, () -> service.addToWhitelist("bad name"));
    }
}
