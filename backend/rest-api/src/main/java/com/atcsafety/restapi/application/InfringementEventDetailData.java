package com.atcsafety.restapi.application;

import java.time.Instant;
import java.util.List;

/**
 * Full detail of a single infringement event including both aircraft trajectories and comments,
 * as returned from the persistence layer through {@link InfringementEventPort#findById(String)}.
 *
 * <p>{@code mongoStatus} holds the raw MongoDB string. Mapping to a display status
 * (CLOSED→PENDING_REVIEW) is the responsibility of {@link EventService}.
 *
 * <p>{@code minSeparationCycleIndex} is 0-indexed into the trajectory arrays and nullable
 * for events persisted before SAFM-46.
 *
 * <p>{@code minVerticalSeparationCycleIndex} is 0-indexed into the trajectory arrays and nullable
 * for events persisted before SAFM-64. Tracked independently of {@code minSeparationCycleIndex}.
 *
 * <p>{@code eventStartCycle} is the 0-based trajectory index of the first infringing position,
 * equal to the number of pre-event context positions prepended. Nullable for events written
 * before SAFM-76.
 *
 * <p>{@code eventEndCycle} is the 0-based trajectory index of the last infringing position,
 * set when the event enters the observation window. Post-event positions follow after this index.
 * Nullable for events written before SAFM-76.
 */
public record InfringementEventDetailData(
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
        Integer minSeparationCycleIndex,
        Integer minVerticalSeparationCycleIndex,
        Integer eventStartCycle,
        Integer eventEndCycle,
        List<TrajectoryPositionData> trajectory1,
        List<TrajectoryPositionData> trajectory2,
        List<CommentData> comments
) {
}
