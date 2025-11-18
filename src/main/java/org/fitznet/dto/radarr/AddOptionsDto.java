package org.fitznet.dto.radarr;

import lombok.Data;

/**
 * DTO for Radarr movie add options.
 */
@Data
public class AddOptionsDto {
    private boolean searchForMovie;

    public AddOptionsDto(boolean searchForMovie) {
        this.searchForMovie = searchForMovie;
    }
}

