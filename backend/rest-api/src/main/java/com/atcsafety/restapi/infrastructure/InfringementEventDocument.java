package com.atcsafety.restapi.infrastructure;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB document representing a separation infringement event, combining the
 * detection-written fields with the review lifecycle state managed by this service.
 *
 * <p>Maps to the {@code separation_infringement_events} collection written by the
 * detection service. The detection service writes all fields except the review-lifecycle
 * fields ({@code status} as "CLOSED", {@code escalationReason}, {@code escalatedAt},
 * {@code dismissedAt}, {@code comments}). Mapping from detection status "CLOSED" to
 * review status "PENDING_REVIEW" is the application layer's responsibility — this class
 * stores the raw string value from MongoDB.
 *
 * <p>Separate from the detection service's own document class — each service owns its
 * persistence model independently.
 */
@Document(collection = "separation_infringement_events")
class InfringementEventDocument {

    @Id
    private String id;
    private String firstAircraftCallsign;
    private String secondAircraftCallsign;
    private long startCycle;
    private Instant startedAt;
    private Instant endedAt;

    /**
     * Raw status string from MongoDB. The detection service writes "CLOSED";
     * the review service writes "PENDING_REVIEW", "ESCALATED", or "DISMISSED".
     * Mapping to {@link com.atcsafety.restapi.domain.ReviewStatus} is done in the application layer.
     */
    private String status;
    private String escalationReason;
    private Instant escalatedAt;
    private Instant dismissedAt;

    /**
     * Running minimum horizontal separation observed during the infringement, in nautical miles.
     * Null for events persisted before SAFM-46 added this field.
     */
    private Double minHorizontalSeparationNm;

    /**
     * Running minimum vertical separation observed during the infringement, in feet.
     * Null for events persisted before SAFM-46 added this field.
     */
    private Double minVerticalSeparationFt;

    /**
     * 0-indexed position in the trajectory arrays at which the minimum horizontal separation
     * occurred during the active-infringement phase. Null for events persisted before SAFM-46.
     */
    private Integer minSeparationCycleIndex;

    /**
     * 0-indexed position in the trajectory arrays at which the minimum vertical separation
     * occurred during the active-infringement phase. Tracked independently of
     * {@code minSeparationCycleIndex} — the two indices may differ (SAFM-64).
     * Null for events persisted before SAFM-64.
     */
    private Integer minVerticalSeparationCycleIndex;

    /**
     * 0-indexed position in the trajectory arrays at which the first infringing cycle begins.
     * Equal to the number of pre-event positions prepended to the trajectory.
     * Null for events persisted before SAFM-76.
     */
    private Integer eventStartCycle;

    /**
     * 0-indexed position in the trajectory arrays at which the last infringing cycle ends.
     * Set when the event transitions to OBSERVATION_WINDOW; post-event positions follow after.
     * Null for events persisted before SAFM-76.
     */
    private Integer eventEndCycle;

    private List<TrajectoryPositionDocument> trajectory1;
    private List<TrajectoryPositionDocument> trajectory2;
    private List<CommentDocument> comments;

    InfringementEventDocument() {
    }

    String getId() { return id; }
    String getFirstAircraftCallsign() { return firstAircraftCallsign; }
    String getSecondAircraftCallsign() { return secondAircraftCallsign; }
    long getStartCycle() { return startCycle; }
    Instant getStartedAt() { return startedAt; }
    Instant getEndedAt() { return endedAt; }
    String getStatus() { return status; }
    String getEscalationReason() { return escalationReason; }
    Instant getEscalatedAt() { return escalatedAt; }
    Instant getDismissedAt() { return dismissedAt; }
    Double getMinHorizontalSeparationNm() { return minHorizontalSeparationNm; }
    Double getMinVerticalSeparationFt() { return minVerticalSeparationFt; }
    Integer getMinSeparationCycleIndex() { return minSeparationCycleIndex; }
    Integer getMinVerticalSeparationCycleIndex() { return minVerticalSeparationCycleIndex; }
    Integer getEventStartCycle() { return eventStartCycle; }
    Integer getEventEndCycle() { return eventEndCycle; }
    List<TrajectoryPositionDocument> getTrajectory1() { return trajectory1; }
    List<TrajectoryPositionDocument> getTrajectory2() { return trajectory2; }
    List<CommentDocument> getComments() { return comments; }
}
