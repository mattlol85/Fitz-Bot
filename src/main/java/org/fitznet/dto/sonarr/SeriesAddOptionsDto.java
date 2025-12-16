package org.fitznet.dto.sonarr;

import lombok.Data;

/**
 * DTO for Sonarr series add options.
 */
@Data
public class SeriesAddOptionsDto {
    private boolean searchForMissingEpisodes;

    public SeriesAddOptionsDto(boolean searchForMissingEpisodes) {
        this.searchForMissingEpisodes = searchForMissingEpisodes;
    }
}

