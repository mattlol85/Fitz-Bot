package org.fitznet;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@SpringBootTest
@TestPropertySource(properties = "discord.bot.token=test-token")
class MainTest {

    @MockBean
    private JDA mockJda;

    @Autowired
    private BotController botController;

    @Test
    void testBotControllerIsCreated() {
        assertNotNull(botController);
    }

    @Test
    void testShutdownEndpoint() {
        String result = botController.shutdown();

        verify(mockJda).shutdown();

        assertNotNull(result);
    }
}
