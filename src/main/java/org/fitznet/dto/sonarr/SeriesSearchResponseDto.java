package org.fitznet.dto.sonarr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO for Sonarr series search API response.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeriesSearchResponseDto {
    private String title;
    private Integer tvdbId;
    private Integer year;
    private String overview;
    private List<String> genres;
    private String status;
    private List<Season> seasons;
}

