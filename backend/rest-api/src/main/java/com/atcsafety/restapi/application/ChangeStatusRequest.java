package com.atcsafety.restapi.application;

/**
 * HTTP request body for {@code PATCH /api/v1/events/{id}/status}.
 *
 * <p>{@code status} must be one of {@code DISMISSED} or {@code ESCALATED}.
 * {@code escalationReason} is required when {@code status} is {@code ESCALATED} and
 * must not be blank; it is ignored (and may be {@code null}) when dismissing.
 *
 * <p>Both fields may be {@code null} when the JSON field is absent — {@link EventService}
 * validates and rejects invalid combinations with a 400.
 */
public record ChangeStatusRequest(String status, String escalationReason) {
}
