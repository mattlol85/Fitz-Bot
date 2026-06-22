package org.fitznet.service;

import lombok.extern.slf4j.Slf4j;
import org.fitznet.util.RconClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Service for managing the Minecraft server's whitelist over RCON.
 */
@Service
@Slf4j
public class MinecraftRconService {

    /** Minecraft Java Edition usernames: 3-16 characters, letters/digits/underscore. */
    private static final Pattern VALID_USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    @Value("${minecraft.rcon.host}")
    private String host;

    @Value("${minecraft.rcon.port}")
    private int port;

    @Value("${minecraft.rcon.password}")
    private String password;

    /**
     * Validates a Minecraft username. Rejecting anything outside the allowed
     * character set also prevents RCON command injection (e.g. embedded spaces
     * or newlines that could append additional commands).
     */
    public static boolean isValidUsername(String username) {
        return username != null && VALID_USERNAME.matcher(username).matches();
    }

    /**
     * Adds a player to the Minecraft whitelist via {@code whitelist add <username>}.
     *
     * @param username the Minecraft username to whitelist
     * @return the raw response text from the Minecraft server
     * @throws IllegalArgumentException if the username is invalid
     * @throws IOException              if the server cannot be reached or auth fails
     */
    public String addToWhitelist(String username) throws IOException {
        if (!isValidUsername(username)) {
            throw new IllegalArgumentException("Invalid Minecraft username: " + username);
        }
        try (RconClient client = new RconClient(host, port)) {
            client.authenticate(password);
            String response = client.sendCommand("whitelist add " + username);
            log.info("RCON whitelist add '{}' -> '{}'", username, response);
            return response;
        }
    }
}
