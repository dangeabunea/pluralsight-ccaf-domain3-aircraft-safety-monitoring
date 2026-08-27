package com.atcsafety.restapi.application;

import java.time.Instant;

/**
 * Analyst comment attached to an infringement event, carried from the persistence
 * layer to the application layer.
 */
public record CommentData(
        String id,
        String text,
        String author,
        Instant createdAt
) {
}
