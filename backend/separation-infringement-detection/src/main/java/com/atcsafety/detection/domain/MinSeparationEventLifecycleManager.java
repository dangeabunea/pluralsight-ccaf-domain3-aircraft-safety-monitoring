package com.atcsafety.detection.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the lifecycle of minimum-separation infringement events across radar cycles.
 *
 * <p>This is the core domain service for Story 02-C (SAFM-37), extended in SAFM-76 to
 * support the {@code OBSERVATION_WINDOW} state. It implements the full
 * {@code ACTIVE → GRACE_PERIOD → OBSERVATION_WINDOW → CLOSED} state machine for each
 * tracked aircraft pair. It is stateless with respect to radar cycles — all event state
 * lives in the {@link MinSeparationActiveEventRegistry}.
 *
 * <p>Pure Java — zero Spring annotations. Wired as a Spring {@code @Bean} in
 * {@code DetectionApplicationConfig}.
 *
 * <h2>Processing model</h2>
 * <p>Called once per radar cycle with the set of pairs currently in infringement and a
 * map of pre-event context positions. For each registered pair NOT in the infringement
 * set, the pair is considered re-separated (including track-loss cases).
 * <ol>
 *   <li>For each infringing pair:
 *     <ul>
 *       <li>If new → create and register event (ACTIVE), prepending pre-event context.</li>
 *       <li>If already ACTIVE → extend active (append positions, update timestamp).</li>
 *       <li>If in GRACE_PERIOD → re-enter active (reset counter, append positions).</li>
 *       <li>If in OBSERVATION_WINDOW → discard event (log WARN), create fresh event.</li>
 *     </ul>
 *   </li>
 *   <li>For each registered pair NOT in the infringement set:
 *     <ul>
 *       <li>If ACTIVE → enter grace period.</li>
 *       <li>If GRACE_PERIOD → decrement counter; if exhausted → enter observation window.</li>
 *       <li>If OBSERVATION_WINDOW → append post-event positions (if aircraft present);
 *           if window complete → close, persist, remove from registry.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>Exception isolation</h2>
 * <p>Each pair evaluation is wrapped in an individual {@code try/catch(RuntimeException)}.
 * A failure on one pair is logged at ERROR level and processing continues for all
 * other pairs in the cycle.
 */
public class MinSeparationEventLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(MinSeparationEventLifecycleManager.class);

    private final MinSeparationActiveEventRegistry registry;
    private final MinSeparationEventStore store;
    private final int gracePeriodCycles;
    private final int observationWindowCycles;
    private final SeparationCalculator separationCalculator;

    /**
     * Constructs a lifecycle manager with the given registry, persistence store,
     * grace-period cycle count, observation-window cycle count, and separation calculator.
     *
     * @param registry                in-memory registry of active events
     * @param store                   persistence port for closed events
     * @param gracePeriodCycles       number of consecutive re-separation cycles before
     *                                entering the observation window
     * @param observationWindowCycles number of post-event cycles to collect before closing
     * @param separationCalculator    domain utility for computing H/V separation
     */
    public MinSeparationEventLifecycleManager(
            MinSeparationActiveEventRegistry registry,
            MinSeparationEventStore store,
            int gracePeriodCycles,
            int observationWindowCycles,
            SeparationCalculator separationCalculator) {
        this.registry = registry;
        this.store = store;
        this.gracePeriodCycles = gracePeriodCycles;
        this.observationWindowCycles = observationWindowCycles;
        this.separationCalculator = separationCalculator;
    }

    /**
     * Processes one radar cycle: updates all active events given the current infringing pairs.
     *
     * @param infringingPairs          set of pairs currently in separation infringement
     * @param cycleNumber              radar cycle number
     * @param cycleTimestamp           UTC timestamp of this radar cycle
     * @param positionsByCallsign      map from callsign to trajectory position for this cycle
     * @param preEventContextByCallsign map from callsign to pre-event position list;
     *                                 used when creating new events to prepend trajectory context
     */
    public void processInfringingPairs(
            Set<AircraftPairKey> infringingPairs,
            long cycleNumber,
            Instant cycleTimestamp,
            Map<String, TrajectoryPosition> positionsByCallsign,
            Map<String, List<TrajectoryPosition>> preEventContextByCallsign) {

        // -- Step 1: Handle infringing pairs --
        for (AircraftPairKey pair : infringingPairs) {
            try {
                processInfringingPair(pair, cycleNumber, cycleTimestamp,
                        positionsByCallsign, preEventContextByCallsign);
            } catch (RuntimeException ex) {
                log.error("Error processing infringing pair [{}] in cycle {}: {} — {}",
                        pair.asString(), cycleNumber, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            }
        }

        // -- Step 2: Handle registered pairs that are no longer infringing this cycle --
        var keysSnapshot = new ArrayList<>(registry.activeKeys());
        for (AircraftPairKey pair : keysSnapshot) {
            if (infringingPairs.contains(pair)) {
                continue;
            }
            try {
                processNonInfringingPair(pair, positionsByCallsign);
            } catch (RuntimeException ex) {
                log.error("Error processing non-infringing pair [{}] in cycle {}: {} — {}",
                        pair.asString(), cycleNumber, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            }
        }
    }

    private void processInfringingPair(
            AircraftPairKey pair,
            long cycleNumber,
            Instant cycleTimestamp,
            Map<String, TrajectoryPosition> positionsByCallsign,
            Map<String, List<TrajectoryPosition>> preEventContextByCallsign) {

        // Guard against missing positions before any state transition.
        var posA = positionsByCallsign.get(pair.callsign1());
        var posB = positionsByCallsign.get(pair.callsign2());
        if (posA == null || posB == null) {
            log.error("Missing position for infringing pair [{}] in cycle {} — skipping. posA present={}, posB present={}",
                    pair.asString(), cycleNumber, posA != null, posB != null);
            return;
        }

        double hNm = separationCalculator.horizontalSeparationNm(posA, posB);
        double vFt = separationCalculator.verticalSeparationFt(posA, posB);

        if (!registry.contains(pair)) {
            // New infringing pair — create event with pre-event context
            List<TrajectoryPosition> pre1 = preEventContextByCallsign.getOrDefault(
                    pair.callsign1(), List.of());
            List<TrajectoryPosition> pre2 = preEventContextByCallsign.getOrDefault(
                    pair.callsign2(), List.of());
            var event = MinSeparationInfringementEvent.createNew(
                    pair, cycleNumber, cycleTimestamp, gracePeriodCycles,
                    pre1, pre2, posA, posB, hNm, vFt);
            registry.register(pair, event);
        } else {
            var event = registry.get(pair);
            if (event == null) {
                log.error("Registry inconsistency: contains()==true but get()==null for pair [{}] in cycle {} — skipping",
                        pair.asString(), cycleNumber);
                return;
            }

            if (event.getStatus() == MinSeparationEventStatus.OBSERVATION_WINDOW) {
                // Re-infringement during observation window: discard old event, create fresh one
                log.warn("Pair [{}] re-infringed during OBSERVATION_WINDOW — discarding event [{}]",
                        pair.asString(), event.getEventId());
                registry.remove(pair);
                List<TrajectoryPosition> pre1 = preEventContextByCallsign.getOrDefault(
                        pair.callsign1(), List.of());
                List<TrajectoryPosition> pre2 = preEventContextByCallsign.getOrDefault(
                        pair.callsign2(), List.of());
                var newEvent = MinSeparationInfringementEvent.createNew(
                        pair, cycleNumber, cycleTimestamp, gracePeriodCycles,
                        pre1, pre2, posA, posB, hNm, vFt);
                registry.register(pair, newEvent);
            } else if (event.getStatus() == MinSeparationEventStatus.ACTIVE) {
                event.extendActive(cycleTimestamp, posA, posB, hNm, vFt);
            } else {
                // GRACE_PERIOD → re-enter active
                event.reenterActive(cycleTimestamp, posA, posB, hNm, vFt);
            }
        }
    }

    private void processNonInfringingPair(
            AircraftPairKey pair,
            Map<String, TrajectoryPosition> positionsByCallsign) {

        var event = registry.get(pair);
        if (event == null) {
            return;
        }

        if (event.getStatus() == MinSeparationEventStatus.ACTIVE) {
            event.enterGracePeriod();

        } else if (event.getStatus() == MinSeparationEventStatus.GRACE_PERIOD) {
            event.decrementGracePeriod();
            if (event.isGracePeriodExhausted()) {
                event.enterObservationWindow();
            }

        } else if (event.getStatus() == MinSeparationEventStatus.OBSERVATION_WINDOW) {
            // Append post-event positions if both aircraft are still visible
            var posA = positionsByCallsign.get(event.getCallsign1());
            var posB = positionsByCallsign.get(event.getCallsign2());
            if (posA != null && posB != null) {
                event.appendPostEventPosition(posA, posB);
            }
            if (event.isObservationWindowComplete(observationWindowCycles)) {
                event.close();
                store.save(event);
                registry.remove(pair);
            }
        }
    }
}
