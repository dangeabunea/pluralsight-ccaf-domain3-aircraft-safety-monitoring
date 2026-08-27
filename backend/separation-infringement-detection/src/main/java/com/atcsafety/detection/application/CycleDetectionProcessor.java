package com.atcsafety.detection.application;

import com.atcsafety.detection.domain.AircraftPairKey;
import com.atcsafety.detection.domain.InfringementDetector;
import com.atcsafety.detection.domain.MinSeparationEventLifecycleManager;
import com.atcsafety.detection.domain.TrajectoryPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates the separation infringement detection pipeline for a completed radar cycle.
 *
 * <p>Converts a cycle's buffered {@link RadarPositionMessage} DTOs to domain
 * {@link TrajectoryPosition} objects, updates the per-aircraft rolling position history,
 * runs separation infringement detection via {@link InfringementDetector}, and advances
 * the event lifecycle state machine via {@link MinSeparationEventLifecycleManager}.
 *
 * <p>The rolling {@link AircraftPositionHistory} buffer is updated for ALL aircraft in
 * every cycle — not just infringing pairs. This provides pre-event context when a new
 * infringement event is created (SAFM-76).
 *
 * <p>Not thread-safe. Single-threaded use is assumed — one Kafka consumer thread
 * processes radar cycles sequentially.
 */
@Component
public class CycleDetectionProcessor {

    private static final Logger log = LoggerFactory.getLogger(CycleDetectionProcessor.class);

    private final InfringementDetector detector;
    private final MinSeparationEventLifecycleManager lifecycleManager;
    private final AircraftPositionHistory positionHistory;

    CycleDetectionProcessor(InfringementDetector detector,
                             MinSeparationEventLifecycleManager lifecycleManager,
                             AircraftPositionHistory positionHistory) {
        this.detector = detector;
        this.lifecycleManager = lifecycleManager;
        this.positionHistory = positionHistory;
    }

    /**
     * Processes one completed radar cycle.
     *
     * <p>Each {@link RadarPositionMessage} in the buffer is converted to a
     * {@link TrajectoryPosition} using an explicit float-to-double widening conversion.
     * The cycle timestamp is parsed from the first message's {@code timestampUTC} field;
     * if parsing fails, the current system time is used and a WARN is logged.
     *
     * <p>After all positions are translated, the rolling {@link AircraftPositionHistory} buffer
     * is updated for every aircraft. Pre-event context snapshots are then taken for each
     * aircraft — these are passed to the lifecycle manager for use when a new event is
     * created for a previously unseen infringing pair.
     *
     * <p>Positions whose {@code flightNb} is {@code null} or blank fall back to the string
     * {@code "TARGET-<targetId>"} as the callsign, preserving all radar track data.
     *
     * <p>If the cycle buffer is empty, a WARN is logged and the method returns immediately
     * without calling the detector.
     *
     * @param cycleBuffer all positions received for this radar cycle; must not be null
     * @param cycleNumber radar cycle sequence number, used in diagnostic logs and domain calls
     */
    public void processCompletedCycle(List<RadarPositionMessage> cycleBuffer, int cycleNumber) {
        if (cycleBuffer.isEmpty()) {
            log.warn("Cycle {} buffer is empty — skipping detection", cycleNumber);
            return;
        }

        Instant cycleTimestamp = parseCycleTimestamp(cycleBuffer.get(0).timestampUTC(), cycleNumber);

        List<TrajectoryPosition> positions = cycleBuffer.stream()
                .map(msg -> TrajectoryPosition.from(
                        resolveCallsign(msg), msg.x(), msg.y(), msg.altFeet(), msg.speedKn(),
                        msg.headingDeg(), msg.lat(), msg.lon(),
                        cycleNumber, cycleTimestamp))
                .toList();

        Map<String, TrajectoryPosition> positionsByCallsign = positions.stream()
                .collect(Collectors.toMap(
                        TrajectoryPosition::callsign,
                        p -> p,
                        (existing, replacement) -> {
                            log.warn("Duplicate callsign '{}' in cycle {} — keeping last position",
                                    existing.callsign(), cycleNumber);
                            return replacement;
                        }));

        // Evict stale tracks BEFORE snapshotting — ensures coasted-out callsigns never
        // pollute the pre-event context map passed to the lifecycle manager.
        positionHistory.evictStaleTracks(positionsByCallsign.keySet());

        // Snapshot pre-event context BEFORE updating the history with the current cycle.
        // This ensures the snapshot contains only the preceding N-1 cycles — the current
        // cycle is NOT in the pre-event list. When a new infringement event is created,
        // the current cycle is supplied separately as positionA/positionB (first infringing
        // position), so there is no duplication between pre-event context and infringement data.
        Map<String, List<TrajectoryPosition>> preEventContextByCallsign = new HashMap<>();
        for (String callsign : positionsByCallsign.keySet()) {
            preEventContextByCallsign.put(callsign, positionHistory.getSnapshot(callsign));
        }

        // Update rolling history AFTER snapshotting — all aircraft, not just infringing pairs.
        for (TrajectoryPosition pos : positions) {
            positionHistory.update(pos);
        }

        Set<AircraftPairKey> infringements = detector.detectInfringements(positions, cycleNumber);
        lifecycleManager.processInfringingPairs(
                infringements, cycleNumber, cycleTimestamp,
                positionsByCallsign, preEventContextByCallsign);
    }

    private Instant parseCycleTimestamp(String timestampUTC, int cycleNumber) {
        try {
            return Instant.parse(timestampUTC);
        } catch (DateTimeParseException | NullPointerException ex) {
            log.warn("Cycle {} has unparseable timestamp '{}' — using current time as fallback",
                    cycleNumber, timestampUTC);
            return Instant.now();
        }
    }

    private String resolveCallsign(RadarPositionMessage msg) {
        return (msg.flightNb() != null && !msg.flightNb().isBlank())
                ? msg.flightNb()
                : "TARGET-" + msg.targetId();
    }
}
