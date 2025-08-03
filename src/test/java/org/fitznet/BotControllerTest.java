package org.fitznet;

import org.fitznet.service.BotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for BotController reset join counts endpoint.
 */
class BotControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BotService botService;

    private BotController botController;

    @BeforeEach
    void setUp() {
        openMocks(this);
        botController = new BotController(botService);
        mockMvc = MockMvcBuilders.standaloneSetup(botController).build();
    }

    @Test
    void testResetJoinCountsEndpoint() throws Exception {
        String guildId = "123456789";
        String expectedResponse = "Reset join counts for 5 users in guild 'Test Guild' (123456789)";

        when(botService.resetGuildJoinCounts(guildId)).thenReturn(expectedResponse);

        mockMvc.perform(post("/bot/reset-join-counts/{guildId}", guildId))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        verify(botService, times(1)).resetGuildJoinCounts(guildId);
    }

    @Test
    void testResetJoinCountsEndpointWithInvalidGuild() throws Exception {
        String guildId = "invalid";
        String expectedResponse = "Invalid guild ID format: invalid";

        when(botService.resetGuildJoinCounts(guildId)).thenReturn(expectedResponse);

        mockMvc.perform(post("/bot/reset-join-counts/{guildId}", guildId))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        verify(botService, times(1)).resetGuildJoinCounts(guildId);
    }

    @Test
    void testResetJoinCountsEndpointWhenBotNotRunning() throws Exception {
        String guildId = "123456789";
        String expectedResponse = "Bot is not running!";

        when(botService.resetGuildJoinCounts(guildId)).thenReturn(expectedResponse);

        mockMvc.perform(post("/bot/reset-join-counts/{guildId}", guildId))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        verify(botService, times(1)).resetGuildJoinCounts(guildId);
    }

    @Test
    void testStartupEndpoint() throws Exception {
        String expectedResponse = "Bot started successfully!";
        when(botService.startBot()).thenReturn(expectedResponse);

        mockMvc.perform(post("/bot/startup"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        verify(botService, times(1)).startBot();
    }

    @Test
    void testStatusEndpoint() throws Exception {
        String expectedResponse = "Bot status: CONNECTED";
        when(botService.getStatus()).thenReturn(expectedResponse);

        mockMvc.perform(get("/bot/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        verify(botService, times(1)).getStatus();
    }

    @Test
    void testShutdownEndpoint() throws Exception {
        String expectedResponse = "Bot is shutting down.";
        when(botService.stopBot()).thenReturn(expectedResponse);

        mockMvc.perform(post("/bot/shutdown"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResponse));

        verify(botService, times(1)).stopBot();
    }
}
