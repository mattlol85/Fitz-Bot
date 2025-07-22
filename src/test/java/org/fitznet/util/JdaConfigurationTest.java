package org.fitznet.util;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdaConfigurationTest {

    @Test
    void testCreateConfiguredJdaWithInvalidToken() {
        // Test that the method handles invalid tokens gracefully
        assertThrows(Exception.class, () -> {
            JdaConfiguration.createConfiguredJda("invalid-token");
        });
    }

    @Test
    void testCreateConfiguredJdaMethodExists() {
        // This test just verifies the method signature is correct
        // We can't test with real tokens in unit tests
        assertNotNull(JdaConfiguration.class.getMethod("createConfiguredJda", String.class));
    }
}