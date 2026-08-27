package com.atcsafety.restapi.application;

import java.time.Instant;
import java.util.List;

/**
 * HTTP response body for {@code GET /api/v1/events/{id}}.
 *
 * <p>{@code status} is always the display status — {@code "CLOSED"} from MongoDB has
 * already been mapped to {@code "PENDING_REVIEW"} by {@link EventService}.
 *
 * <p>{@code trajectory1} and {@code trajectory2} are ordered by {@code radarCycle} ascending
 * and share the same indices — {@code trajectory1[i].radarCycle == trajectory2[i].radarCycle}
 * for all i (alignment contract, decision log 2026-03-21).
 *
 * <p>{@code comments} are ordered by {@code createdAt} ascending, with {@code id} as
 * a stable secondary sort for simultaneous timestamps.
 *
 * <p>{@code minVerticalSeparationCycleIndex} is the 0-based index of the cycle where the
 * true minimum vertical separation was observed. Tracked independently of
 * {@code minSeparationCycleIndex} — the two may differ (SAFM-64). Null for events written
 * before SAFM-64.
 *
 * <p>{@code eventStartCycle} is the 0-based trajectory index of the first infringing cycle
 * (i.e. the number of pre-event context positions prepended). The UI uses this to mark
 * where the infringement window begins. Null for events written before SAFM-76.
 *
 * <p>{@code eventEndCycle} is the 0-based trajectory index of the last infringing cycle.
 * Post-event observation-window positions follow after this index. Null for events written
 * before SAFM-76.
 */
public record EventDetailResponse(
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
        Integer minSeparationCycleIndex,
        Integer minVerticalSeparationCycleIndex,
        Integer eventStartCycle,
        Integer eventEndCycle,
        List<TrajectoryPositionData> trajectory1,
        List<TrajectoryPositionData> trajectory2,
        List<CommentData> comments
) {
}
