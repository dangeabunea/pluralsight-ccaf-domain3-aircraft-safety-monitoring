package com.atcsafety.restapi.application;

import java.time.Instant;

/**
 * HTTP response body for {@code PATCH /api/v1/events/{id}/status}.
 *
 * <p>For a DISMISSED transition: {@code escalationReason} and {@code escalatedAt}
 * are {@code null}; {@code dismissedAt} is set to the UTC timestamp of the transition.
 *
 * <p>For an ESCALATED transition: {@code dismissedAt} is {@code null};
 * {@code escalationReason} and {@code escalatedAt} are set from the domain object.
 */
public record ChangeStatusResponse(
        String id,
        String status,
        String escalationReason,
        Instant escalatedAt,
        Instant dismissedAt) {
}
