package com.atcsafety.detection.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link SeparationCalculator}.
 *
 * <p>All tests are pure Java — no Spring context, no Kafka, no MongoDB.
 * Each test constructs {@link TrajectoryPosition} instances directly and
 * asserts on the computed separation values.
 */
class SeparationCalculatorTest {

    private static final long CYCLE = 1L;
    private static final Instant TS = Instant.EPOCH;

    private final SeparationCalculator calculator = new SeparationCalculator();

    private TrajectoryPosition pos(String callsign, float x, float y, float altFeet) {
        return TrajectoryPosition.from(callsign, x, y, altFeet, 0.0f, 0, null, null, CYCLE, TS);
    }

    @Nested
    class CalculateHorizontalSeparation {

        /**
         * AC1: Aircraft at identical positions — separation must be exactly 0.0 NM.
         */
        @Test
        void should_return_zero_when_positions_are_identical() {
            var posA = pos("BA123", 0.0f, 0.0f, 10000.0f);
            var posB = pos("AF456", 0.0f, 0.0f, 10000.0f);

            double result = calculator.horizontalSeparationNm(posA, posB);

            assertThat(result).isEqualTo(0.0);
        }

        /**
         * AC2: Aircraft B at (9260, 0) from origin — exactly 5.0 NM (9260 / 1852 = 5.0).
         */
        @Test
        void should_return_five_nm_when_aircraft_is_9260_metres_apart_on_x_axis() {
            var posA = pos("BA123", 0.0f, 0.0f, 10000.0f);
            var posB = pos("AF456", 9260.0f, 0.0f, 10000.0f);

            double result = calculator.horizontalSeparationNm(posA, posB);

            assertThat(result).isEqualTo(5.0);
        }

        /**
         * AC3: 3-4-5 triangle in metres: sqrt(3000^2 + 4000^2) = 5000 m = 5000/1852 ≈ 2.700 NM.
         */
        @Test
        void should_return_approximately_2700_nm_when_positions_form_3_4_5_triangle_in_metres() {
            var posA = pos("BA123", 0.0f, 0.0f, 10000.0f);
            var posB = pos("AF456", 3000.0f, 4000.0f, 10000.0f);

            double result = calculator.horizontalSeparationNm(posA, posB);

            assertThat(result).isCloseTo(2.700, within(0.001));
        }

        /**
         * AC4: Horizontal separation must never be negative — non-negativity invariant.
         * Euclidean distance is always >= 0, but this test guards against upstream data
         * corruption (NaN, Infinity) or implementation errors.
         */
        @Test
        void should_return_non_negative_separation_for_any_two_positions() {
            var posA = pos("BA123", 1000.0f, 2000.0f, 15000.0f);
            var posB = pos("AF456", 3000.0f, 1000.0f, 18000.0f);

            double result = calculator.horizontalSeparationNm(posA, posB);

            assertThat(result).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Nested
    class CalculateVerticalSeparation {

        /**
         * AC5: Aircraft A at 20000 ft, aircraft B at 21500 ft — vertical separation = 1500 ft.
         */
        @Test
        void should_return_1500_ft_when_aircraft_differ_by_1500_ft_ascending() {
            var posA = pos("BA123", 0.0f, 0.0f, 20000.0f);
            var posB = pos("AF456", 0.0f, 0.0f, 21500.0f);

            double result = calculator.verticalSeparationFt(posA, posB);

            assertThat(result).isEqualTo(1500.0);
        }

        /**
         * AC6: Aircraft A at 35000 ft, aircraft B at 33000 ft — vertical separation = 2000 ft
         * (inverted order confirms absolute difference, not signed).
         */
        @Test
        void should_return_2000_ft_when_aircraft_differ_by_2000_ft_descending_order() {
            var posA = pos("BA123", 0.0f, 0.0f, 35000.0f);
            var posB = pos("AF456", 0.0f, 0.0f, 33000.0f);

            double result = calculator.verticalSeparationFt(posA, posB);

            assertThat(result).isEqualTo(2000.0);
        }

        /**
         * AC7: Aircraft at same altitude — vertical separation = 0 ft.
         */
        @Test
        void should_return_zero_when_aircraft_are_at_same_altitude() {
            var posA = pos("BA123", 0.0f, 0.0f, 25000.0f);
            var posB = pos("AF456", 0.0f, 0.0f, 25000.0f);

            double result = calculator.verticalSeparationFt(posA, posB);

            assertThat(result).isEqualTo(0.0);
        }
    }
}
