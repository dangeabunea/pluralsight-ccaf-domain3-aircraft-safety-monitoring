package com.atcsafety.restapi.domain;

import java.time.Instant;

/**
 * An immutable reviewer comment attached to an infringement review.
 *
 * <p>Comments are append-only — once created they are never modified or deleted.
 * The {@code id} is assigned by the application layer before persistence.
 */
public record Comment(String id, String text, String author, Instant createdAt) {
}
