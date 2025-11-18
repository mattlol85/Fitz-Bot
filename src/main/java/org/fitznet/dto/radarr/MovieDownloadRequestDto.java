package org.fitznet.dto.radarr;

import lombok.Data;

/**
 * DTO for Radarr movie download request.
 */
@Data
public class MovieDownloadRequestDto {
    private Integer tmdbId;
    private Integer qualityProfileId;
    private String rootFolderPath;
    private boolean monitored;
    private AddOptionsDto addOptions;

    public MovieDownloadRequestDto(Integer tmdbId, Integer qualityProfileId, String rootFolderPath) {
        this.tmdbId = tmdbId;
        this.qualityProfileId = qualityProfileId;
        this.rootFolderPath = rootFolderPath;
        this.monitored = true;
        this.addOptions = new AddOptionsDto(true);
    }
}

