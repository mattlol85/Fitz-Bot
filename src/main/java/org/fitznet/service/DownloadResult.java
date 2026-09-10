package org.fitznet.service;

/**
 * Outcome of an "add to library" call against Radarr/Sonarr.
 */
public enum DownloadResult {
    /** The item was added successfully. */
    ADDED,
    /** The item is already in the library — not an error. */
    ALREADY_EXISTS,
    /** The add failed (HTTP error, connectivity, validation). */
    FAILED
}
