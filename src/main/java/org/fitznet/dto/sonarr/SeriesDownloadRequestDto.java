package org.fitznet.dto.sonarr;

import lombok.Data;

import java.util.List;

/**
 * DTO for Sonarr series download request.
 */
@Data
public class SeriesDownloadRequestDto {
    private Integer tvdbId;
    private String title;
    private Integer qualityProfileId;
    private String rootFolderPath;
    private boolean monitored;
    private boolean seasonFolder;
    private SeriesAddOptionsDto addOptions;
    private List<Season> seasons;

    public SeriesDownloadRequestDto(Integer tvdbId, String title, Integer qualityProfileId,
                                     String rootFolderPath, List<Season> seasons) {
        this.tvdbId = tvdbId;
        this.title = title;
        this.qualityProfileId = qualityProfileId;
        this.rootFolderPath = rootFolderPath;
        this.monitored = true;
        this.seasonFolder = true;
        this.addOptions = new SeriesAddOptionsDto(true);
        this.seasons = seasons;
    }
}

