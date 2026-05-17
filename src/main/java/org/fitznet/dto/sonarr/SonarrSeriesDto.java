package org.fitznet.dto.sonarr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO for a Sonarr library series entry.
 * Maps to the SeriesResource schema from GET /api/v3/series.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarrSeriesDto {
    private Integer id;
    private String title;
    private Integer tvdbId;
}

