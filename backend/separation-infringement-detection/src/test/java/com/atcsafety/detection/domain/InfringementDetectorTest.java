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

    /**
     * Helper: position at a horizontal offset expressed in nautical miles, converted to the
     * metres the detector's geometry actually operates on.
     */
    private TrajectoryPosition posNm(String callsign, double xNm, double altFeet) {
        return pos(callsign, (float) (xNm * NM_TO_METRES), (float) altFeet);
    }

    @Nested
    class DetectInfringements {

        @Test
        void should_flag_pair_as_infringing_when_both_separation_thresholds_are_breached() {
            // arrange
            var ba123 = posNm("BA123", 0.0, 20000);
            var af456 = posNm("AF456", 4.9, 20999);

            // act
            var result = detector.detectInfringements(List.of(ba123, af456), CYCLE);

            // assert
            assertThat(result).containsExactly(AircraftPairKey.of("BA123", "AF456"));
        }

        @Test
        void should_not_detect_infringement_when_horizontal_separation_is_exactly_at_threshold() {
            // arrange
            var ba123 = posNm("BA123", 0.0, 20000);
            var af456 = posNm("AF456", 5.0, 20999);

            // act
            var result = detector.detectInfringements(List.of(ba123, af456), CYCLE);

            // assert
            assertThat(result).isEmpty();
        }

        @Test
        void should_not_detect_infringement_when_vertical_separation_is_exactly_at_threshold() {
            // arrange
            var ba123 = posNm("BA123", 0.0, 20000);
            var af456 = posNm("AF456", 4.9, 21000);

            // act
            var result = detector.detectInfringements(List.of(ba123, af456), CYCLE);

            // assert
            assertThat(result).isEmpty();
        }

        @Test
        void should_not_detect_infringement_when_neither_threshold_is_breached() {
            // arrange
            var ba123 = posNm("BA123", 0.0, 20000);
            var af456 = posNm("AF456", 5.1, 21100);

            // act
            var result = detector.detectInfringements(List.of(ba123, af456), CYCLE);

            // assert
            assertThat(result).isEmpty();
        }

        @Test
        void should_return_all_three_pairs_when_three_aircraft_are_mutually_within_thresholds() {
            // arrange
            var aa001 = posNm("AA001", 0.0, 20000);
            var bb002 = posNm("BB002", 4.9, 20999);
            var cc003 = posNm("CC003", 2.0, 20500);

            // act
            var result = detector.detectInfringements(List.of(aa001, bb002, cc003), CYCLE);

            // assert
            assertThat(result).containsExactlyInAnyOrder(
                    AircraftPairKey.of("AA001", "BB002"),
                    AircraftPairKey.of("AA001", "CC003"),
                    AircraftPairKey.of("BB002", "CC003"));
        }
    }
}
