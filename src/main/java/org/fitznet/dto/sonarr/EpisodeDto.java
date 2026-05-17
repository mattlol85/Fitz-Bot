package org.fitznet.dto.sonarr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO for a single episode returned by the Sonarr episode endpoint.
 * Maps to the EpisodeResource schema from GET /api/v3/episode.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EpisodeDto {
    private Integer id;
    private Integer seriesId;
    private Integer seasonNumber;
    private Integer episodeNumber;
    private String title;
    private boolean hasFile;
    private boolean monitored;
}

