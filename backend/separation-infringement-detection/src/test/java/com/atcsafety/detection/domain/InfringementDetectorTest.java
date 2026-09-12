package com.atcsafety.detection.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses a real {@link SeparationCalculator} and {@link SeparationThresholds} (5.0 NM, 1000 ft).
 *
 */
class InfringementDetectorTest {

    private static final double NM_TO_METRES = 1852.0;
    private static final long CYCLE = 1L;
    private static final Instant TS = Instant.EPOCH;

    private final SeparationCalculator calculator = new SeparationCalculator();
    private final SeparationThresholds thresholds = new SeparationThresholds(5.0, 1000);
    private final InfringementDetector detector = new InfringementDetector(calculator, thresholds);

    /**
     * Helper: position at x metres on x-axis from origin, altitude in feet.
     */
    private TrajectoryPosition pos(String callsign, float xMetres, float altFeet) {
        return TrajectoryPosition.from(callsign, xMetres, 0.0f, altFeet, 0.0f, 0, null, null, CYCLE, TS);
    }
}
