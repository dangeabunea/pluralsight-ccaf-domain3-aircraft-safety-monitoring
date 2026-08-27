package com.atcsafety.restapi.application;

import java.time.Instant;
import java.util.List;

/**
 * Data record carrying the raw fields of one infringement event document from
 * the persistence layer to the application layer.
 *
 * <p>{@code mongoStatus} holds the raw MongoDB string value (e.g. {@code "CLOSED"},
 * {@code "PENDING_REVIEW"}, {@code "ESCALATED"}, {@code "DISMISSED"}). Mapping to
 * a display status is the responsibility of {@link EventService}, not this record.
 *
 * <p>{@code minHorizontalSeparationNm} and {@code minVerticalSeparationFt} are nullable
 * because older events (written before SAFM-46) may not carry these fields.
 *
 * <p>{@code comments} is included here so that {@link EventService} can derive
 * {@code commentCount} without issuing an additional MongoDB query — the document
 * is loaded in full by the list query and comments are mapped in-memory.
 */
public record InfringementEventData(
        String id,
        String firstAircraftCallsign,
        String secondAircraftCallsign,
        Instant startedAt,
        Instant endedAt,
        String mongoStatus,
        String escalationReason,
        Instant escalatedAt,
        Instant dismissedAt,
        Double minHorizontalSeparationNm,
        Double minVerticalSeparationFt,
        List<CommentData> comments
) {
}
