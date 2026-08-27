package com.atcsafety.detection.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects aircraft pairs in separation infringement for a single radar cycle.
 *
 * <p>Given a set of {@link TrajectoryPosition} snapshots from one radar cycle, this class
 * checks every unordered pair (N*(N-1)/2 pairs) and returns the set of pairs whose
 * horizontal AND vertical separation simultaneously fall strictly below the configured
 * thresholds. Equality at either threshold is not an infringement (strict less-than).
 *
 * <p>This class is a stateless detection predicate — it performs a pure geometry check
 * per cycle. It has no knowledge of whether a pair was already active in a previous cycle.
 * Temporal state (ACTIVE, GRACE_PERIOD, CLOSED) is the responsibility of the event
 * registry (Story 02-C).
 *
 * <p>Pure Java — zero Spring, Kafka, or MongoDB imports. Instantiated directly
 * (not a Spring bean) and wired via {@code DetectionApplicationConfig}.
 */
public class InfringementDetector {

    private static final Logger log = LoggerFactory.getLogger(InfringementDetector.class);

    private static final long SLOW_CYCLE_THRESHOLD_MS = 500;

    private final SeparationCalculator calculator;
    private final SeparationThresholds thresholds;

    public InfringementDetector(SeparationCalculator calculator, SeparationThresholds thresholds) {
        this.calculator = calculator;
        this.thresholds = thresholds;
    }

    /**
     * Identifies all aircraft pairs in separation infringement in the given radar cycle.
     *
     * <p>Both thresholds use strict less-than: a pair is infringed when
     * {@code hSep < horizontalThresholdNm} AND {@code vSep < verticalThresholdFt}.
     *
     * <p>If the cycle contains zero aircraft, an ERROR is logged and an empty set is returned.
     * If the cycle contains exactly one aircraft, a WARN is logged and an empty set is returned.
     * In both cases the diagnostic is recorded and processing continues normally.
     *
     * <p>If processing the cycle takes longer than 500 ms, a WARN is logged with the cycle
     * number and elapsed time.
     *
     * @param positions   all aircraft positions reported in this radar cycle; must not be null
     * @param cycleNumber the radar cycle sequence number, used in diagnostic log messages
     * @return unordered set of pair keys whose separation breaches both thresholds;
     *         never null; empty when no infringement exists or the cycle is too small to analyse
     */
    public Set<AircraftPairKey> detectInfringements(List<TrajectoryPosition> positions, long cycleNumber) {
        if (positions.isEmpty()) {
            log.error("Radar cycle {} contains no aircraft positions — skipping separation analysis", cycleNumber);
            return Set.of();
        }
        if (positions.size() == 1) {
            log.warn("Radar cycle {} contains only one aircraft — skipping separation analysis", cycleNumber);
            return Set.of();
        }

        long startNanos = System.nanoTime();
        Set<AircraftPairKey> infringedPairs = new HashSet<>();

        for (int i = 0; i < positions.size() - 1; i++) {
            for (int j = i + 1; j < positions.size(); j++) {
                TrajectoryPosition a = positions.get(i);
                TrajectoryPosition b = positions.get(j);

                double hSep = calculator.horizontalSeparationNm(a, b);
                double vSep = calculator.verticalSeparationFt(a, b);

                if (hSep < thresholds.horizontalThresholdNm() && vSep < thresholds.verticalThresholdFt()) {
                    infringedPairs.add(AircraftPairKey.of(a.callsign(), b.callsign()));
                }
            }
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs > SLOW_CYCLE_THRESHOLD_MS) {
            log.warn("Radar cycle {} separation analysis took {} ms — threshold is {} ms",
                    cycleNumber, elapsedMs, SLOW_CYCLE_THRESHOLD_MS);
        }

        return infringedPairs;
    }
}
