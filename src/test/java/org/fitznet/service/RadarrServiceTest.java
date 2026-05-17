package org.fitznet.service;

import org.fitznet.dto.radarr.MovieSearchResponseDto;
import org.fitznet.dto.radarr.RadarrQueueItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RadarrService.
 */
class RadarrServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RadarrService radarrService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ReflectionTestUtils.setField(radarrService, "host", "localhost");
        ReflectionTestUtils.setField(radarrService, "port", "7878");
        ReflectionTestUtils.setField(radarrService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(radarrService, "qualityProfileId", 1);
        ReflectionTestUtils.setField(radarrService, "rootFolderPath", "P:\\Plex\\Movies");

        radarrService.init();
    }

    // ── searchMovies tests ──────────────────────────────────────────────────────

    @Test
    void testSearchMovies_Success() {
        MovieSearchResponseDto[] mockResponse = {
                createMovie("Dune", 438631),
                createMovie("Dune: Part Two", 693134)
        };
        ResponseEntity<MovieSearchResponseDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(MovieSearchResponseDto[].class))).thenReturn(responseEntity);

        List<MovieSearchResponseDto> results = radarrService.searchMovies("Dune");

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Dune", results.get(0).getTitle());
    }

    @Test
    void testSearchMovies_LimitToFiveResults() {
        MovieSearchResponseDto[] mockResponse = new MovieSearchResponseDto[10];
        for (int i = 0; i < 10; i++) {
            mockResponse[i] = createMovie("Movie " + i, i);
        }
        ResponseEntity<MovieSearchResponseDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(MovieSearchResponseDto[].class))).thenReturn(responseEntity);

        List<MovieSearchResponseDto> results = radarrService.searchMovies("Movie");

        assertNotNull(results);
        assertEquals(5, results.size());
    }

    @Test
    void testSearchMovies_NullBody() {
        ResponseEntity<MovieSearchResponseDto[]> responseEntity =
                new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(MovieSearchResponseDto[].class))).thenReturn(responseEntity);

        List<MovieSearchResponseDto> results = radarrService.searchMovies("Something");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchMovies_Exception() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(MovieSearchResponseDto[].class))).thenThrow(new RuntimeException("Connection refused"));

        List<MovieSearchResponseDto> results = radarrService.searchMovies("Dune");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ── getQueueDetails tests ───────────────────────────────────────────────────

    @Test
    void testGetQueueDetails_Success() {
        RadarrQueueItemDto[] mockResponse = {
                createQueueItem("Dune: Part Two", "downloading", 1_000_000.0, 400_000.0),
                createQueueItem("Gladiator II", "queued", 800_000.0, 800_000.0)
        };
        ResponseEntity<RadarrQueueItemDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(RadarrQueueItemDto[].class))).thenReturn(responseEntity);

        List<RadarrQueueItemDto> results = radarrService.getQueueDetails();

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Dune: Part Two", results.get(0).getTitle());
        assertEquals("downloading", results.get(0).getStatus());
        assertEquals(1_000_000.0, results.get(0).getSize());
        assertEquals(400_000.0, results.get(0).getSizeleft());
    }

    @Test
    void testGetQueueDetails_EmptyQueue() {
        RadarrQueueItemDto[] mockResponse = new RadarrQueueItemDto[0];
        ResponseEntity<RadarrQueueItemDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(RadarrQueueItemDto[].class))).thenReturn(responseEntity);

        List<RadarrQueueItemDto> results = radarrService.getQueueDetails();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetQueueDetails_NullBody() {
        ResponseEntity<RadarrQueueItemDto[]> responseEntity =
                new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(RadarrQueueItemDto[].class))).thenReturn(responseEntity);

        List<RadarrQueueItemDto> results = radarrService.getQueueDetails();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetQueueDetails_ExceptionIsPropagated() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(RadarrQueueItemDto[].class))).thenThrow(new RuntimeException("Radarr is down"));

        assertThrows(RuntimeException.class, () -> radarrService.getQueueDetails());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private MovieSearchResponseDto createMovie(String title, int tmdbId) {
        MovieSearchResponseDto dto = new MovieSearchResponseDto();
        dto.setTitle(title);
        dto.setTmdbId(tmdbId);
        dto.setYear(2024);
        return dto;
    }

    private RadarrQueueItemDto createQueueItem(String title, String status, Double size, Double sizeleft) {
        RadarrQueueItemDto dto = new RadarrQueueItemDto();
        dto.setTitle(title);
        dto.setStatus(status);
        dto.setTrackedDownloadStatus("ok");
        dto.setSize(size);
        dto.setSizeleft(sizeleft);
        return dto;
    }
}
