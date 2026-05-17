package org.fitznet.dto.sonarr;

import lombok.Data;

import java.util.List;

/**
 * DTO for triggering an episode search command via the Sonarr command endpoint.
 * Maps to POST /api/v3/command with name "EpisodeSearch".
 */
@Data
public class EpisodeSearchCommandDto {
    private String name;
    private List<Integer> episodeIds;

    public EpisodeSearchCommandDto(List<Integer> episodeIds) {
        this.name = "EpisodeSearch";
        this.episodeIds = episodeIds;
    }
}

