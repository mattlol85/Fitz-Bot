package org.fitznet.service;

import lombok.extern.slf4j.Slf4j;
import org.fitznet.dto.radarr.MovieDownloadRequestDto;
import org.fitznet.dto.radarr.MovieSearchResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    /**
     * Search for movies using Radarr API.
     *
     * @param searchTerm the movie name to search for
     * @return list of up to 5 movie results
     */
    public List<MovieSearchResponseDto> searchMovies(String searchTerm) {
        try {
            String url = String.format("http://%s:%s/api/v3/movie/lookup?term=%s", host, port, searchTerm);
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
     * @return true if successful, false otherwise
     */
    public boolean downloadMovie(int tmdbId, String movieTitle) {
        try {
            String url = String.format("http://%s:%s/api/v3/movie", host, port);
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
                return true;
            } else {
                log.warn("Radarr returned non-success status for movie {}: {}", movieTitle, response.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Error adding movie '{}' (TMDB: {}) to Radarr: {}", movieTitle, tmdbId, e.getMessage(), e);

            // Check if it's a duplicate movie error
            if (e.getMessage() != null && (e.getMessage().contains("already") || e.getMessage().contains("exists"))) {
                log.info("Movie '{}' already exists in Radarr", movieTitle);
            }

            return false;
        }
    }
}

