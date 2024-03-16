package org.fitznet;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MainTest {
    private MockedStatic<JDABuilder> mockedJDABuilder;

    @BeforeEach
    void setUp() {
        mockedJDABuilder = Mockito.mockStatic(JDABuilder.class);
    }

    @Test
    void testSetupBotClient() throws InterruptedException {
        // Prepare
        JDABuilder mockBuilder = mock(JDABuilder.class, RETURNS_DEEP_STUBS);
        JDA mockJda = mock(JDA.class);

        when(JDABuilder.createDefault(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setStatus(any())).thenReturn(mockBuilder);
        when(mockBuilder.setActivity(any(Activity.class))).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockJda);

        // Act
        String token = "testToken";
        Main.setupBotClient(token);

        // Assert
        mockedJDABuilder.verify(() -> JDABuilder.createDefault(token));
        verify(mockBuilder).setStatus(any());
        verify(mockBuilder).setActivity(any(Activity.class));
        verify(mockBuilder).build();
        verify(mockJda).addEventListener(any());
        verify(mockJda).awaitReady();

        mockedJDABuilder.close();
    }
}
