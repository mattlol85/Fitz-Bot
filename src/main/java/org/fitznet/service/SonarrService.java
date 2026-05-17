package org.fitznet.service;

import lombok.extern.slf4j.Slf4j;
import org.fitznet.dto.sonarr.EpisodeDto;
import org.fitznet.dto.sonarr.EpisodeSearchCommandDto;
import org.fitznet.dto.sonarr.Season;
import org.fitznet.dto.sonarr.SeriesDownloadRequestDto;
import org.fitznet.dto.sonarr.SeriesSearchResponseDto;
import org.fitznet.dto.sonarr.SonarrQueueItemDto;
import org.fitznet.dto.sonarr.SonarrSeriesDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Service for interacting with Sonarr API to search and download TV shows.
 */
@Service
@Slf4j
public class SonarrService {

    @Value("${joenet.host}")
    private String host;

    @Value("${joenet.sonarr.port}")
    private String port;

    @Value("${joenet.sonarr.apikey}")
    private String apiKey;

    @Value("${joenet.sonarr.quality-profile-id}")
    private int qualityProfileId;

    @Value("${joenet.sonarr.root-folder-path}")
    private String rootFolderPath;

    @Autowired
    private RestTemplate restTemplate;

    // Computed once after properties are injected
    private String baseUrl;

    @PostConstruct
    void init() {
        String h = host == null ? "" : host.trim();
        String p = port == null ? "" : port.trim();

        if (h.isEmpty()) {
            throw new IllegalStateException("SonarrService misconfigured: joenet.host is blank");
        }
        if (p.isEmpty()) {
            // Default to Sonarr's common port if not provided
            p = "8989";
            log.warn("joenet.sonarr.port was blank; defaulting to {}", p);
        }
        // Fail fast if port is not numeric
        if (!p.chars().allMatch(Character::isDigit)) {
            throw new IllegalStateException("SonarrService misconfigured: joenet.sonarr.port must be numeric, got '" + p + "'");
        }

        this.host = h;
        this.port = p;
        this.baseUrl = String.format("http://%s:%s/api/v3", this.host, this.port);
        log.info("SonarrService configured: {}", this.baseUrl);
    }

    /**
     * Search for TV series using Sonarr API.
     *
     * @param searchTerm the series name to search for
     * @return list of up to 5 series results
     */
    public List<SeriesSearchResponseDto> searchSeries(String searchTerm) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/series/lookup")
                    .queryParam("term", searchTerm)
                    .build()
                    .encode()
                    .toUriString();
            log.info("Searching Sonarr for series: {}", searchTerm);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SeriesSearchResponseDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    SeriesSearchResponseDto[].class
            );

            if (response.getBody() == null) {
                log.warn("Sonarr search returned null body for term: {}", searchTerm);
                return new ArrayList<>();
            }

            List<SeriesSearchResponseDto> results = Arrays.asList(response.getBody());
            log.info("Found {} series for search term: {}", results.size(), searchTerm);

            // Limit to 5 results
            return results.size() > 5 ? results.subList(0, 5) : results;

        } catch (Exception e) {
            log.error("Error searching Sonarr for term '{}': {}", searchTerm, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Add a TV series to Sonarr and start download.
     *
     * @param tvdbId      the TVDB ID of the series
     * @param seriesTitle the title of the series (for logging)
     * @param seasons     the list of seasons to monitor
     * @return true if successful, false otherwise
     */
    public boolean downloadSeries(int tvdbId, String seriesTitle, List<Season> seasons) {
        try {
            String url = baseUrl + "/series";
            log.info("Adding series to Sonarr: {} (TVDB: {}) with {} seasons", seriesTitle, tvdbId, seasons.size());

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            headers.set("Content-Type", "application/json");

            SeriesDownloadRequestDto requestDto = new SeriesDownloadRequestDto(
                    tvdbId,
                    seriesTitle,
                    qualityProfileId,
                    rootFolderPath,
                    seasons
            );
            HttpEntity<SeriesDownloadRequestDto> entity = new HttpEntity<>(requestDto, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully added series to Sonarr: {}", seriesTitle);
                return true;
            } else {
                log.warn("Sonarr returned non-success status for series {}: {}", seriesTitle, response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Error adding series '{}' (TVDB: {}) to Sonarr: {}", seriesTitle, tvdbId, e.getMessage(), e);

            // Check if it's a duplicate series error
            if (e.getMessage() != null && (e.getMessage().contains("already") || e.getMessage().contains("exists"))) {
                log.info("Series '{}' already exists in Sonarr", seriesTitle);
            }

            return false;
        }
    }

    /**
     * Retrieves the current download queue details from Sonarr.
     *
     * @return list of queue items, or empty list on error
     */
    public List<SonarrQueueItemDto> getQueueDetails() {
        try {
            String url = baseUrl + "/queue/details";
            log.info("Fetching Sonarr queue details");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SonarrQueueItemDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    SonarrQueueItemDto[].class
            );

            if (response.getBody() == null) {
                log.warn("Sonarr queue/details returned null body");
                return new ArrayList<>();
            }

            List<SonarrQueueItemDto> items = Arrays.asList(response.getBody());
            log.info("Sonarr queue has {} item(s)", items.size());
            return items;

        } catch (Exception e) {
            log.error("Error fetching Sonarr queue details: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Looks up a series in the Sonarr library by its TVDB ID.
     *
     * @param tvdbId the TVDB ID of the series
     * @return the first matching {@link SonarrSeriesDto}, or {@code null} if not in the library
     */
    public SonarrSeriesDto getSeriesByTvdbId(int tvdbId) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/series")
                    .queryParam("tvdbId", tvdbId)
                    .build()
                    .toUriString();
            log.info("Looking up Sonarr library series by TVDB ID: {}", tvdbId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<SonarrSeriesDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    SonarrSeriesDto[].class
            );

            if (response.getBody() == null || response.getBody().length == 0) {
                log.info("Series with TVDB ID {} is not in the Sonarr library", tvdbId);
                return null;
            }

            SonarrSeriesDto found = response.getBody()[0];
            log.info("Found series '{}' in Sonarr library with internal ID: {}", found.getTitle(), found.getId());
            return found;

        } catch (Exception e) {
            log.error("Error looking up series by TVDB ID {}: {}", tvdbId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieves all episodes for a specific season of a series already in the Sonarr library.
     *
     * @param sonarrSeriesId the internal Sonarr series ID (from {@link #getSeriesByTvdbId})
     * @param seasonNumber   the season number to fetch episodes for
     * @return list of episodes sorted by episode number, or empty list on error
     */
    public List<EpisodeDto> getEpisodes(int sonarrSeriesId, int seasonNumber) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/episode")
                    .queryParam("seriesId", sonarrSeriesId)
                    .queryParam("seasonNumber", seasonNumber)
                    .build()
                    .toUriString();
            log.info("Fetching episodes for Sonarr series ID {} season {}", sonarrSeriesId, seasonNumber);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<EpisodeDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    EpisodeDto[].class
            );

            if (response.getBody() == null) {
                log.warn("Sonarr episode endpoint returned null body");
                return new ArrayList<>();
            }

            List<EpisodeDto> episodes = new ArrayList<>(Arrays.asList(response.getBody()));
            episodes.sort(Comparator.comparingInt(e -> e.getEpisodeNumber() != null ? e.getEpisodeNumber() : 0));
            log.info("Found {} episodes for series ID {} season {}", episodes.size(), sonarrSeriesId, seasonNumber);
            return episodes;

        } catch (Exception e) {
            log.error("Error fetching episodes for series ID {} season {}: {}", sonarrSeriesId, seasonNumber, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Triggers an episode search (download) for the given episode IDs via the Sonarr command endpoint.
     *
     * @param episodeIds the Sonarr internal episode IDs to search for
     * @return true if the command was accepted, false otherwise
     */
    public boolean triggerEpisodeSearch(List<Integer> episodeIds) {
        try {
            String url = baseUrl + "/command";
            log.info("Triggering Sonarr EpisodeSearch for episode IDs: {}", episodeIds);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            headers.set("Content-Type", "application/json");

            EpisodeSearchCommandDto commandDto = new EpisodeSearchCommandDto(episodeIds);
            HttpEntity<EpisodeSearchCommandDto> entity = new HttpEntity<>(commandDto, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully triggered episode search for IDs: {}", episodeIds);
                return true;
            } else {
                log.warn("Sonarr command returned non-success status: {}", response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Error triggering episode search for IDs {}: {}", episodeIds, e.getMessage(), e);
            return false;
        }
    }
}

