package org.fitznet.service;

/**
 * Thrown when an upstream metadata provider (Sonarr's SkyHook/TVDB, Radarr's TMDB) is
 * temporarily unavailable, as opposed to a search that legitimately returned zero results.
 *
 * <p>Lets the command layer show a "try again shortly" message instead of "no results found".
 */
public class MediaSearchException extends RuntimeException {

    public MediaSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
