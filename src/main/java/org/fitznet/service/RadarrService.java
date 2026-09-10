package org.fitznet.service;

import lombok.extern.slf4j.Slf4j;
import org.fitznet.dto.radarr.MovieDownloadRequestDto;
import org.fitznet.dto.radarr.MovieSearchResponseDto;
import org.fitznet.dto.radarr.RadarrQueueItemDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service for interacting with Radarr API to search and download movies.
 */
@Service
@Slf4j
public class RadarrService {

    @Value("${joenet.host}")
    private String host;

    @Value("${joenet.radarr.port}")
    private String port;

    @Value("${joenet.radarr.apikey}")
    private String apiKey;

    @Value("${joenet.radarr.quality-profile-id}")
    private int qualityProfileId;

    @Value("${joenet.radarr.root-folder-path}")
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
            throw new IllegalStateException("RadarrService misconfigured: joenet.host is blank");
        }
        if (p.isEmpty()) {
            // Default to Radarr's common port if not provided
            p = "7878";
            log.warn("joenet.radarr.port was blank; defaulting to {}", p);
        }
        // Fail fast if port is not numeric
        if (!p.chars().allMatch(Character::isDigit)) {
            throw new IllegalStateException("RadarrService misconfigured: joenet.radarr.port must be numeric, got '" + p + "'");
        }

        this.host = h;
        this.port = p;
        this.baseUrl = String.format("http://%s:%s/api/v3", this.host, this.port);
        log.info("RadarrService configured: {}", this.baseUrl);
    }

    /**
     * Search for movies using Radarr API.
     *
     * @param searchTerm the movie name to search for
     * @return list of up to 5 movie results
     */
    public List<MovieSearchResponseDto> searchMovies(String searchTerm) {
        try {
            URI url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/movie/lookup")
                    .queryParam("term", searchTerm)
                    .build()
                    .encode()
                    .toUri();
            log.info("Searching Radarr for movies: {}", searchTerm);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<MovieSearchResponseDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MovieSearchResponseDto[].class
            );

            if (response.getBody() == null) {
                log.warn("Radarr search returned null body for term: {}", searchTerm);
                return new ArrayList<>();
            }

            List<MovieSearchResponseDto> results = Arrays.asList(response.getBody());
            log.info("Found {} movies for search term: {}", results.size(), searchTerm);

            // Limit to 5 results
            return results.size() > 5 ? results.subList(0, 5) : results;

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429) {
                log.warn("Radarr metadata lookup unavailable for '{}': {} {}",
                        searchTerm, e.getStatusCode(), e.getStatusText());
                throw new MediaSearchException("Radarr metadata provider unavailable", e);
            }
            log.error("Error searching Radarr for term '{}': {}", searchTerm, e.getMessage(), e);
            return new ArrayList<>();
        } catch (ResourceAccessException e) {
            log.warn("Radarr unreachable while searching for '{}': {}", searchTerm, e.getMessage());
            throw new MediaSearchException("Radarr unreachable", e);
        } catch (Exception e) {
            log.error("Error searching Radarr for term '{}': {}", searchTerm, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Add a movie to Radarr and start download.
     *
     * @param tmdbId     the TMDB ID of the movie
     * @param movieTitle the title of the movie (for logging)
     * @return {@link DownloadResult#ADDED}, {@link DownloadResult#ALREADY_EXISTS}, or {@link DownloadResult#FAILED}
     */
    public DownloadResult downloadMovie(int tmdbId, String movieTitle) {
        try {
            String url = baseUrl + "/movie";
            log.info("Adding movie to Radarr: {} (TMDB: {})", movieTitle, tmdbId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            headers.set("Content-Type", "application/json");

            MovieDownloadRequestDto requestDto = new MovieDownloadRequestDto(tmdbId, qualityProfileId, rootFolderPath);
            HttpEntity<MovieDownloadRequestDto> entity = new HttpEntity<>(requestDto, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully added movie to Radarr: {}", movieTitle);
                return DownloadResult.ADDED;
            }
            log.warn("Radarr returned non-success status for movie {}: {}", movieTitle, response.getStatusCode());
            return DownloadResult.FAILED;

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            if (body != null && (body.contains("already") || body.contains("exists")
                    || body.contains("ExistsValidator"))) {
                log.info("Movie '{}' (TMDB: {}) is already in the Radarr library", movieTitle, tmdbId);
                return DownloadResult.ALREADY_EXISTS;
            }
            log.error("Radarr rejected add for '{}' (TMDB: {}): {} {}",
                    movieTitle, tmdbId, e.getStatusCode(), body);
            return DownloadResult.FAILED;
        } catch (Exception e) {
            log.error("Error adding movie '{}' (TMDB: {}) to Radarr", movieTitle, tmdbId, e);
            return DownloadResult.FAILED;
        }
    }

    /**
     * Retrieves the current download queue details from Radarr.
     *
     * @return list of queue items, or empty list on error
     */
    public List<RadarrQueueItemDto> getQueueDetails() {
        try {
            String url = baseUrl + "/queue/details";
            log.info("Fetching Radarr queue details");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<RadarrQueueItemDto[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    RadarrQueueItemDto[].class
            );

            if (response.getBody() == null) {
                log.warn("Radarr queue/details returned null body");
                return new ArrayList<>();
            }

            List<RadarrQueueItemDto> items = Arrays.asList(response.getBody());
            log.info("Radarr queue has {} item(s)", items.size());
            return items;

        } catch (Exception e) {
            log.error("Error fetching Radarr queue details: {}", e.getMessage(), e);
            throw e;
        }
    }
}
