package org.fitznet.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JoenetCommands#truncate(String, int)}.
 */
class JoenetCommandsHelperTest {

    @Test
    void testTruncate_ShorterThanMax_ReturnsUnchanged() {
        assertEquals("Dune (2021)", JoenetCommands.truncate("Dune (2021)", 100));
    }

    @Test
    void testTruncate_ExactlyMax_ReturnsUnchanged() {
        String s = "a".repeat(100);
        assertEquals(s, JoenetCommands.truncate(s, 100));
    }

    @Test
    void testTruncate_LongerThanMax_CutsAndAppendsEllipsis() {
        String longTitle = "Borat Subsequent Moviefilm: " + "x".repeat(110); // 138 chars
        String result = JoenetCommands.truncate(longTitle, 100);

        assertEquals(100, result.length());
        assertTrue(result.endsWith("..."));
        assertTrue(longTitle.startsWith(result.substring(0, 97)));
    }

    @Test
    void testTruncate_NullInput_ReturnsEmptyString() {
        assertEquals("", JoenetCommands.truncate(null, 100));
    }

    @Test
    void testTruncate_MaxThreeOrLess_CutsWithoutEllipsis() {
        assertEquals("ab", JoenetCommands.truncate("abcdef", 2));
    }
}
