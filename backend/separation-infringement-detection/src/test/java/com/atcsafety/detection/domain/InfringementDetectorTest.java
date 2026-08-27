package com.atcsafety.detection.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InfringementDetector}.
 *
 * <p>Pure Java — no Spring context, no Kafka, no MongoDB.
 * Uses a real {@link SeparationCalculator} and {@link SeparationThresholds} (6.0 NM, 2500 ft).
 *
 * <p>Position coordinates are chosen so that the resulting separations match the boundary
 * values specified in the acceptance criteria. The base setup places aircraft at identical
 * positions (0 separation) unless overridden; tests then choose coordinates that produce
 * the required hSep/vSep values:
 * <ul>
 *   <li>hSep = 5.9 NM: x-distance = 5.9 * 1852 = 10926.8 m</li>
 *   <li>hSep = 6.0 NM: x-distance = 6.0 * 1852 = 11112.0 m</li>
 *   <li>hSep = 6.1 NM: x-distance = 6.1 * 1852 = 11297.2 m</li>
 * </ul>
 */
class InfringementDetectorTest {

    private static final double NM_TO_METRES = 1852.0;
    private static final long CYCLE = 1L;
    private static final Instant TS = Instant.EPOCH;

    private final SeparationCalculator calculator = new SeparationCalculator();
    private final SeparationThresholds thresholds = new SeparationThresholds(6.0, 2500);
    private final InfringementDetector detector = new InfringementDetector(calculator, thresholds);

    /**
     * Helper: position at x metres on x-axis from origin, altitude in feet.
     */
    private TrajectoryPosition pos(String callsign, float xMetres, float altFeet) {
        return TrajectoryPosition.from(callsign, xMetres, 0.0f, altFeet, 0.0f, 0, null, null, CYCLE, TS);
    }

    @Nested
    class DetectInfringements {

        @Test
        void should_detect_infringement_when_both_thresholds_are_breached() {
            // hSep = 5.9 NM (< 6.0), vSep = 2499 ft (< 2500) — both strictly below threshold
            var a = pos("BA123", 0.0f, 20000.0f);
            var b = pos("AF456", (float) (5.9 * NM_TO_METRES), 22499.0f);

            var result = detector.detectInfringements(List.of(a, b), 1);

            assertThat(result).containsExactlyInAnyOrder(AircraftPairKey.of("BA123", "AF456"));
        }

        @Test
        void should_not_detect_infringement_when_horizontal_separation_equals_threshold() {
            // hSep = 6.0 NM (not below threshold — strict less-than), vSep = 2499 ft
            var a = pos("BA123", 0.0f, 20000.0f);
            var b = pos("AF456", (float) (6.0 * NM_TO_METRES), 22499.0f);

            var result = detector.detectInfringements(List.of(a, b), 1);

            assertThat(result).isEmpty();
        }

        @Test
        void should_not_detect_infringement_when_vertical_separation_equals_threshold() {
            // hSep = 5.9 NM (< 6.0), vSep = 2500 ft (not below threshold — strict less-than)
            var a = pos("BA123", 0.0f, 20000.0f);
            var b = pos("AF456", (float) (5.9 * NM_TO_METRES), 22500.0f);

            var result = detector.detectInfringements(List.of(a, b), 1);

            assertThat(result).isEmpty();
        }

        @Test
        void should_not_detect_infringement_when_neither_threshold_is_breached() {
            // hSep = 6.1 NM (> 6.0), vSep = 2600 ft (> 2500)
            var a = pos("BA123", 0.0f, 20000.0f);
            var b = pos("AF456", (float) (6.1 * NM_TO_METRES), 22600.0f);

            var result = detector.detectInfringements(List.of(a, b), 1);

            assertThat(result).isEmpty();
        }

        @Test
        void should_return_three_pairs_when_three_aircraft_all_within_thresholds() {
            // A, B, C all at positions within 5.9 NM and 2499 ft of each other
            // Use origin cluster: A at (0,0), B and C within range
            var a = pos("AA001", 0.0f, 20000.0f);
            var b = pos("BB002", (float) (5.9 * NM_TO_METRES), 22499.0f);
            var c = pos("CC003", (float) (2.0 * NM_TO_METRES), 20500.0f);

            var result = detector.detectInfringements(List.of(a, b, c), 1);

            assertThat(result).containsExactlyInAnyOrder(
                    AircraftPairKey.of("AA001", "BB002"),
                    AircraftPairKey.of("AA001", "CC003"),
                    AircraftPairKey.of("BB002", "CC003")
            );
        }

        @Test
        void should_return_two_independent_pairs_when_aircraft_A_infringes_with_both_B_and_C() {
            // A close to both B and C; B and C far from each other
            var a = pos("AA001", 0.0f, 20000.0f);
            // Place B far from C on y-axis to ensure B-C pair is not infringed
            var bFar = TrajectoryPosition.from("BB002",
                    (float) (1.0 * NM_TO_METRES), (float) (4.6 * NM_TO_METRES), 20300.0f, 0.0f,
                    0, null, null, CYCLE, TS);
            var cFar = TrajectoryPosition.from("CC003",
                    (float) (1.0 * NM_TO_METRES), (float) (-4.6 * NM_TO_METRES), 20100.0f, 0.0f,
                    0, null, null, CYCLE, TS);

            var result = detector.detectInfringements(List.of(a, bFar, cFar), 1);

            assertThat(result).containsExactlyInAnyOrder(
                    AircraftPairKey.of("AA001", "BB002"),
                    AircraftPairKey.of("AA001", "CC003")
            );
            assertThat(result).doesNotContain(AircraftPairKey.of("BB002", "CC003"));
        }

        @Test
        void should_return_empty_set_and_skip_analysis_when_cycle_has_no_aircraft() {
            var result = detector.detectInfringements(List.of(), 1);

            assertThat(result).isEmpty();
        }

        @Test
        void should_return_empty_set_and_skip_analysis_when_cycle_has_only_one_aircraft() {
            var a = pos("BA123", 0.0f, 20000.0f);

            var result = detector.detectInfringements(List.of(a), 1);

            assertThat(result).isEmpty();
        }
    }
}
