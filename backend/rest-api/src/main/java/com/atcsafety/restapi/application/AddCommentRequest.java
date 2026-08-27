package com.atcsafety.restapi.application;

/**
 * HTTP request body for {@code POST /api/v1/events/{id}/comments}.
 *
 * <p>{@code text} and {@code author} may each be {@code null} when the JSON field is
 * absent or explicitly null — {@link EventService} validates and rejects null with a 400.
 */
public record AddCommentRequest(String text, String author) {
}
