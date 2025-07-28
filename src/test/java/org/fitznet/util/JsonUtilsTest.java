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
    private static final String TEST_DATABASE_FILENAME = "test_database.json";

    @Test
    void testMapperIsNotNull() {
        assertNotNull(MAPPER);
    }

    @Test
    void testMapperCanReadWriteJson(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve(TEST_DATABASE_FILENAME).toFile();

        Map<Long, Long> testData = new HashMap<>();
        testData.put(123L, 5L);
        testData.put(456L, 10L);

        MAPPER.writeValue(testFile, testData);

        assertTrue(testFile.exists());
        Map<Long, Long> readData = MAPPER.readValue(testFile, new TypeReference<>() {});
        assertEquals(2, readData.size());
        assertTrue(readData.containsKey(123L));
        assertEquals(5L, readData.get(123L));
        assertEquals(10L, readData.get(456L));
    }

    @Test
    void testJsonUtilsMapper() {
        assertNotNull(JsonUtils.MAPPER);
        assertSame(ObjectMapper.class, JsonUtils.MAPPER.getClass());
    }

}
