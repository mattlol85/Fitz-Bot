package org.fitznet.dto.radarr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO for Radarr movie search API response.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MovieSearchResponseDto {
    private String title;
    private String originalTitle;
    private Integer tmdbId;
    private Integer year;
    private String overview;
    private List<String> genres;
    private String imdbId;
    private String status;
}

