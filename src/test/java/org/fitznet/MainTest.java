package org.fitznet;

import org.fitznet.service.BotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = "discord.bot.token=test-token")
@Execution(ExecutionMode.SAME_THREAD)
class MainTest {

    @MockBean
    private BotService mockBotService;

    @Autowired
    private BotController botController;

    @Test
    void testBotControllerIsCreated() {
        assertNotNull(botController);
    }

    @Test
    void testShutdownEndpoint() {
        // Mock the BotService response
        when(mockBotService.stopBot()).thenReturn("Bot is shutting down.");

        String result = botController.shutdown();

        assertNotNull(result);
        assertEquals("Bot is shutting down.", result);

        // Verify that BotService.stopBot() was called
        verify(mockBotService).stopBot();
    }
}
