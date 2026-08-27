package com.atcsafety.detection.infrastructure;

import com.atcsafety.detection.domain.MinSeparationEventStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB document for a persisted separation infringement event.
 *
 * <p>Intentionally simple — no custom field names, no indexes required for the demo.
 * Spring Data MongoDB serialises {@link MinSeparationEventStatus} as its string name automatically.
 *
 * <p>Only {@link com.atcsafety.detection.infrastructure.MinSeparationInfringementStore} creates
 * instances of this class; application and domain code work exclusively with
 * {@link com.atcsafety.detection.domain.MinSeparationInfringementEvent}.
 *
 * <p>{@code startedAt} and {@code endedAt} are ISO-8601 {@link Instant} values.
 * {@code endedAt} corresponds to {@code lastActiveCycleTimestamp} on the domain aggregate —
 * the timestamp of the last cycle where both thresholds were simultaneously breached.
 * Grace-period cycle timestamps are NOT used for {@code endedAt}.
 *
 * <p>{@code trajectory1} and {@code trajectory2} contain only ACTIVE infringement cycle
 * positions (never grace-period positions). They are index-aligned: entry {@code i} in
 * each array corresponds to the same radar cycle.
 *
 * <p>{@code minHorizontalSeparationNm}, {@code minVerticalSeparationFt},
 * {@code minSeparationCycleIndex}, and {@code minVerticalSeparationCycleIndex} capture
 * the closest-approach data across all ACTIVE cycles. The two cycle indices are tracked
 * independently — vertical and horizontal minima may occur at different trajectory positions
 * (SAFM-64). All indices are 0-based into the trajectory arrays.
 *
 * <p>{@code eventStartCycle} is the 0-based trajectory index of the first infringing cycle
 * (equal to the number of pre-event positions prepended). {@code eventEndCycle} is the
 * 0-based index of the last infringing cycle. Together they define the infringement window
 * within the full trajectory array for UI replay purposes (SAFM-76).
 */
@Document(collection = "separation_infringement_events")
class MinSeparationInfringementDocument {

    @Id
    private final String id;
    private final String firstAircraftCallsign;
    private final String secondAircraftCallsign;
    private final long startCycle;
    private final Instant startedAt;
    private final Instant endedAt;
    private final MinSeparationEventStatus status;
    private final List<TrajectoryPositionDocument> trajectory1;
    private final List<TrajectoryPositionDocument> trajectory2;
    private final double minHorizontalSeparationNm;
    private final double minVerticalSeparationFt;
    private final int minSeparationCycleIndex;
    private final int minVerticalSeparationCycleIndex;
    private final int eventStartCycle;
    private final int eventEndCycle;

    MinSeparationInfringementDocument(
            String id,
            String firstAircraftCallsign,
            String secondAircraftCallsign,
            long startCycle,
            Instant startedAt,
            Instant endedAt,
            MinSeparationEventStatus status,
            List<TrajectoryPositionDocument> trajectory1,
            List<TrajectoryPositionDocument> trajectory2,
            double minHorizontalSeparationNm,
            double minVerticalSeparationFt,
            int minSeparationCycleIndex,
            int minVerticalSeparationCycleIndex,
            int eventStartCycle,
            int eventEndCycle) {
        this.id = id;
        this.firstAircraftCallsign = firstAircraftCallsign;
        this.secondAircraftCallsign = secondAircraftCallsign;
        this.startCycle = startCycle;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.trajectory1 = trajectory1;
        this.trajectory2 = trajectory2;
        this.minHorizontalSeparationNm = minHorizontalSeparationNm;
        this.minVerticalSeparationFt = minVerticalSeparationFt;
        this.minSeparationCycleIndex = minSeparationCycleIndex;
        this.minVerticalSeparationCycleIndex = minVerticalSeparationCycleIndex;
        this.eventStartCycle = eventStartCycle;
        this.eventEndCycle = eventEndCycle;
    }

    String getId() { return id; }
    String getFirstAircraftCallsign() { return firstAircraftCallsign; }
    String getSecondAircraftCallsign() { return secondAircraftCallsign; }
    long getStartCycle() { return startCycle; }
    Instant getStartedAt() { return startedAt; }
    Instant getEndedAt() { return endedAt; }
    MinSeparationEventStatus getStatus() { return status; }
    List<TrajectoryPositionDocument> getTrajectory1() { return trajectory1; }
    List<TrajectoryPositionDocument> getTrajectory2() { return trajectory2; }
    double getMinHorizontalSeparationNm() { return minHorizontalSeparationNm; }
    double getMinVerticalSeparationFt() { return minVerticalSeparationFt; }
    int getMinSeparationCycleIndex() { return minSeparationCycleIndex; }
    int getMinVerticalSeparationCycleIndex() { return minVerticalSeparationCycleIndex; }
    int getEventStartCycle() { return eventStartCycle; }
    int getEventEndCycle() { return eventEndCycle; }
}
