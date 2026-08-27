package com.atcsafety.restapi.application;

/**
 * Response body for {@code GET /api/v1/events/summary}.
 *
 * <p>{@code pendingCount} includes both {@code CLOSED} documents from the detection service
 * (not yet reviewed) and {@code PENDING_REVIEW} documents (already entered the review queue).
 */
public record EventSummaryResponse(long pendingCount, long escalatedCount) {
}
