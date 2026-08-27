package com.atcsafety.restapi.application;

import java.time.Instant;

/**
 * One item in the paginated response for {@code GET /api/v1/events}.
 *
 * <p>{@code status} is always the display status — {@code "CLOSED"} from MongoDB has
 * already been mapped to {@code "PENDING_REVIEW"} by {@link EventService} before
 * this record is constructed.
 *
 * <p>{@code minHorizontalSeparationNm} and {@code minVerticalSeparationFt} are nullable
 * for events persisted before SAFM-46 added these fields.
 *
 * <p>{@code summary} is nullable when either separation value is absent (pre-SAFM-46 events).
 *
 * <p>{@code type} is always {@code "SeparationMinimaInfringement"} — not stored in MongoDB,
 * set by {@link EventService} to classify the event for the frontend.
 */
public record EventListItemResponse(
        String id,
        String firstAircraftCallsign,
        String secondAircraftCallsign,
        Instant startedAt,
        Instant endedAt,
        String status,
        String escalationReason,
        Instant escalatedAt,
        Instant dismissedAt,
        Double minHorizontalSeparationNm,
        Double minVerticalSeparationFt,
        int commentCount,
        String type,
        String summary
) {
}
