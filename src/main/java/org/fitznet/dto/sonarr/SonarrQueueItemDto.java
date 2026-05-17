package org.fitznet.dto.sonarr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO for a single item in the Sonarr download queue.
 * Maps to the QueueResource schema from GET /api/v3/queue/details.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarrQueueItemDto {
    private String title;
    private String status;
    private String trackedDownloadStatus;
    private String trackedDownloadState;
    private Double size;
    private Double sizeleft;
    private String estimatedCompletionTime;
    private String downloadClient;
    private String errorMessage;
}
