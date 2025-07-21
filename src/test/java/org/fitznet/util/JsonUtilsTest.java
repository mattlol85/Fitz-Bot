package org.fitznet.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class JsonUtilsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testMapperIsNotNull() {
        assertNotNull(MAPPER);
    }

    @Test
    void testRemoveTestUserWithValidFile(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve("test_database.json").toFile();

        Map<Long, Long> testData = new HashMap<>();
        testData.put(123L, 5L);
        testData.put(456L, 10L);

        MAPPER.writeValue(testFile, testData);

        assertTrue(testFile.exists());
        Map<Long, Long> readData = MAPPER.readValue(testFile, new TypeReference<>() {});
        assertEquals(2, readData.size());
        assertTrue(readData.containsKey(123L));
    }

    public static void removeTestUser(Long userId) {
        File file = new File(Constants.TOTALLY_LEGIT_DATABASE_FILENAME);
        try {
            if (file.exists()) {
                Map<Long, Long> counts = MAPPER.readValue(file, new TypeReference<>() {});

                if (counts.containsKey(userId)) {
                    counts.remove(userId);
                    // Save the updated data back to the file
                    MAPPER.writeValue(file, counts);
                    log.info("User with ID {} has been removed.", userId);
                } else {
                    log.info("User with ID {} not found.", userId);
                }
            } else {
                log.error("File not found: " + Constants.TOTALLY_LEGIT_DATABASE_FILENAME);
            }
        } catch (IOException e) {
            log.error("Error processing file: {}", e.getMessage());
        }
    }
}
