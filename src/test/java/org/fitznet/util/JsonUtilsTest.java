package org.fitznet.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.fitznet.util.Constants.TOTALLY_LEGIT_DATABASE_FILENAME;

@Slf4j
public class JsonUtilsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void removeTestUser(Long userId) {
        File file = new File(TOTALLY_LEGIT_DATABASE_FILENAME);
        try {
            // Check if the file exists
            if (file.exists()) {
                // Read the current data from the file
                Map<Long, Long> counts = MAPPER.readValue(file, new TypeReference<>() {});

                // Remove the test user with the specified ID
                if (counts.containsKey(userId)) {
                    counts.remove(userId);
                    // Save the updated data back to the file
                    MAPPER.writeValue(file, counts);
                    log.info("User with ID " + userId + " has been removed.");
                } else {
                    log.info("User with ID " + userId + " not found.");
                }
            } else {
                log.error("File not found: " + TOTALLY_LEGIT_DATABASE_FILENAME);
            }
        } catch (IOException e) {
            log.error("Error processing file: " + e.getMessage());
        }
    }
}
