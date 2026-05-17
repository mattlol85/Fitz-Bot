package org.fitznet.service;

import org.fitznet.dto.sonarr.Season;
import org.fitznet.dto.sonarr.SeriesSearchResponseDto;
import org.fitznet.dto.sonarr.SonarrQueueItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SonarrService.
 */
class SonarrServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private SonarrService sonarrService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up required properties
        ReflectionTestUtils.setField(sonarrService, "host", "localhost");
        ReflectionTestUtils.setField(sonarrService, "port", "8989");
        ReflectionTestUtils.setField(sonarrService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(sonarrService, "qualityProfileId", 4);
        ReflectionTestUtils.setField(sonarrService, "rootFolderPath", "P:\\\\Plex\\\\Tv Shows");

        // Initialize the service
        sonarrService.init();
    }

    @Test
    void testSearchSeries_Success() {
        // Arrange
        String searchTerm = "Breaking Bad";
        SeriesSearchResponseDto[] mockResponse = createMockSeriesArray();

        ResponseEntity<SeriesSearchResponseDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SeriesSearchResponseDto[].class)
        )).thenReturn(responseEntity);

        // Act
        List<SeriesSearchResponseDto> results = sonarrService.searchSeries(searchTerm);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Breaking Bad", results.get(0).getTitle());
        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SeriesSearchResponseDto[].class)
        );
    }

    @Test
    void testSearchSeries_EmptyResults() {
        // Arrange
        String searchTerm = "NonexistentShow";
        SeriesSearchResponseDto[] mockResponse = new SeriesSearchResponseDto[0];

        ResponseEntity<SeriesSearchResponseDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SeriesSearchResponseDto[].class)
        )).thenReturn(responseEntity);

        // Act
        List<SeriesSearchResponseDto> results = sonarrService.searchSeries(searchTerm);

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchSeries_LimitToFiveResults() {
        // Arrange
        String searchTerm = "Show";
        SeriesSearchResponseDto[] mockResponse = createMockSeriesArrayWithSize(10);

        ResponseEntity<SeriesSearchResponseDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SeriesSearchResponseDto[].class)
        )).thenReturn(responseEntity);

        // Act
        List<SeriesSearchResponseDto> results = sonarrService.searchSeries(searchTerm);

        // Assert
        assertNotNull(results);
        assertEquals(5, results.size());
    }

    @Test
    void testSearchSeries_Exception() {
        // Arrange
        String searchTerm = "Breaking Bad";

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SeriesSearchResponseDto[].class)
        )).thenThrow(new RuntimeException("Connection error"));

        // Act
        List<SeriesSearchResponseDto> results = sonarrService.searchSeries(searchTerm);

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testDownloadSeries_Success() {
        // Arrange
        int tvdbId = 81189;
        String seriesTitle = "Breaking Bad";
        List<Season> seasons = Arrays.asList(
                new Season(1, true),
                new Season(2, true),
                new Season(3, true)
        );

        ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.CREATED);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        // Act
        boolean result = sonarrService.downloadSeries(tvdbId, seriesTitle, seasons);

        // Assert
        assertTrue(result);
        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void testDownloadSeries_Failure() {
        // Arrange
        int tvdbId = 81189;
        String seriesTitle = "Breaking Bad";
        List<Season> seasons = Arrays.asList(new Season(1, true));

        ResponseEntity<String> responseEntity = new ResponseEntity<>("{}", HttpStatus.BAD_REQUEST);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        // Act
        boolean result = sonarrService.downloadSeries(tvdbId, seriesTitle, seasons);

        // Assert
        assertFalse(result);
    }

    @Test
    void testDownloadSeries_Exception() {
        // Arrange
        int tvdbId = 81189;
        String seriesTitle = "Breaking Bad";
        List<Season> seasons = Arrays.asList(new Season(1, true));

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RuntimeException("Connection error"));

        // Act
        boolean result = sonarrService.downloadSeries(tvdbId, seriesTitle, seasons);

        // Assert
        assertFalse(result);
    }

    @Test
    void testInit_Success() {
        // This test verifies that init() runs successfully with valid configuration
        // The setup in @BeforeEach already tests this
        assertNotNull(sonarrService);
    }

    @Test
    void testInit_BlankHost() {
        // Arrange
        SonarrService newService = new SonarrService();
        ReflectionTestUtils.setField(newService, "host", "");
        ReflectionTestUtils.setField(newService, "port", "8989");

        // Act & Assert
        assertThrows(IllegalStateException.class, newService::init);
    }

    @Test
    void testInit_InvalidPort() {
        // Arrange
        SonarrService newService = new SonarrService();
        ReflectionTestUtils.setField(newService, "host", "localhost");
        ReflectionTestUtils.setField(newService, "port", "invalid");

        // Act & Assert
        assertThrows(IllegalStateException.class, newService::init);
    }

    // Helper methods

    private SeriesSearchResponseDto[] createMockSeriesArray() {
        SeriesSearchResponseDto series1 = new SeriesSearchResponseDto();
        series1.setTitle("Breaking Bad");
        series1.setTvdbId(81189);
        series1.setYear(2008);
        series1.setStatus("Ended");
        series1.setGenres(Arrays.asList("Crime", "Drama", "Thriller"));

        List<Season> seasons = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            seasons.add(new Season(i, false));
        }
        series1.setSeasons(seasons);

        SeriesSearchResponseDto series2 = new SeriesSearchResponseDto();
        series2.setTitle("Better Call Saul");
        series2.setTvdbId(273181);
        series2.setYear(2015);
        series2.setStatus("Ended");
        series2.setGenres(Arrays.asList("Crime", "Drama"));

        List<Season> seasons2 = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            seasons2.add(new Season(i, false));
        }
        series2.setSeasons(seasons2);

        return new SeriesSearchResponseDto[]{series1, series2};
    }

    private SeriesSearchResponseDto[] createMockSeriesArrayWithSize(int size) {
        SeriesSearchResponseDto[] array = new SeriesSearchResponseDto[size];
        for (int i = 0; i < size; i++) {
            SeriesSearchResponseDto series = new SeriesSearchResponseDto();
            series.setTitle("Show " + (i + 1));
            series.setTvdbId(i + 1);
            series.setYear(2020 + i);
            array[i] = series;
        }
        return array;
    }

    // ── getQueueDetails tests ───────────────────────────────────────────────────

    @Test
    void testGetQueueDetails_Success() {
        SonarrQueueItemDto[] mockResponse = {
                createQueueItem("Breaking Bad S01E01", "downloading", 500_000.0, 200_000.0),
                createQueueItem("Better Call Saul S06E03", "queued", 400_000.0, 400_000.0)
        };
        ResponseEntity<SonarrQueueItemDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(SonarrQueueItemDto[].class))).thenReturn(responseEntity);

        List<SonarrQueueItemDto> results = sonarrService.getQueueDetails();

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Breaking Bad S01E01", results.get(0).getTitle());
        assertEquals("downloading", results.get(0).getStatus());
        assertEquals(500_000.0, results.get(0).getSize());
        assertEquals(200_000.0, results.get(0).getSizeleft());
    }

    @Test
    void testGetQueueDetails_EmptyQueue() {
        SonarrQueueItemDto[] mockResponse = new SonarrQueueItemDto[0];
        ResponseEntity<SonarrQueueItemDto[]> responseEntity =
                new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(SonarrQueueItemDto[].class))).thenReturn(responseEntity);

        List<SonarrQueueItemDto> results = sonarrService.getQueueDetails();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetQueueDetails_NullBody() {
        ResponseEntity<SonarrQueueItemDto[]> responseEntity =
                new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(SonarrQueueItemDto[].class))).thenReturn(responseEntity);

        List<SonarrQueueItemDto> results = sonarrService.getQueueDetails();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetQueueDetails_ExceptionIsPropagated() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(SonarrQueueItemDto[].class))).thenThrow(new RuntimeException("Sonarr is down"));

        assertThrows(RuntimeException.class, () -> sonarrService.getQueueDetails());
    }

    private SonarrQueueItemDto createQueueItem(String title, String status, Double size, Double sizeleft) {
        SonarrQueueItemDto dto = new SonarrQueueItemDto();
        dto.setTitle(title);
        dto.setStatus(status);
        dto.setTrackedDownloadStatus("ok");
        dto.setSize(size);
        dto.setSizeleft(sizeleft);
        return dto;
    }
}

