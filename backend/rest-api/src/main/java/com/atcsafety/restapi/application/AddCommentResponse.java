package com.atcsafety.restapi.application;

import java.time.Instant;

/**
 * HTTP response body for a successfully created comment.
 * Returned with HTTP 201 Created from {@code POST /api/v1/events/{id}/comments}.
 *
 * <p>Both {@code id} and {@code createdAt} are server-generated — never taken from
 * the request body.
 */
public record AddCommentResponse(String id, String text, String author, Instant createdAt) {
}
