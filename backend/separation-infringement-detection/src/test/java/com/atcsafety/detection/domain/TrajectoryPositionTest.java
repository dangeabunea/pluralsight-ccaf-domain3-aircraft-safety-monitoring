package com.atcsafety.detection.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrajectoryPositionTest {

    private static final Instant CYCLE_TIME = Instant.parse("2026-03-01T10:00:00Z");

    @Nested
    class From {

        @Test
        void should_widen_float_x_to_double_when_created_via_factory() {
            var position = TrajectoryPosition.from("BA123", 9260.0f, 0.0f, 10000.0f, 0.0f, 90, null, null, 42L, CYCLE_TIME);

            assertThat(position.x()).isEqualTo((double) 9260.0f);
        }

        @Test
        void should_widen_float_y_to_double_when_created_via_factory() {
            var position = TrajectoryPosition.from("BA123", 3000.0f, 4000.0f, 10000.0f, 0.0f, 90, null, null, 42L, CYCLE_TIME);

            assertThat(position.y()).isEqualTo((double) 4000.0f);
        }

        @Test
        void should_widen_float_alt_feet_to_double_when_created_via_factory() {
            var position = TrajectoryPosition.from("AF456", 0.0f, 0.0f, 20000.0f, 0.0f, 90, null, null, 42L, CYCLE_TIME);

            assertThat(position.altFeet()).isEqualTo((double) 20000.0f);
        }

        @Test
        void should_store_callsign_when_created_via_factory() {
            var position = TrajectoryPosition.from("KL887", 0.0f, 0.0f, 10000.0f, 0.0f, 90, null, null, 42L, CYCLE_TIME);

            assertThat(position.callsign()).isEqualTo("KL887");
        }

        @Test
        void should_store_radar_cycle_when_created_via_factory() {
            var position = TrajectoryPosition.from("BA123", 0.0f, 0.0f, 10000.0f, 0.0f, 90, null, null, 101L, CYCLE_TIME);

            assertThat(position.radarCycle()).isEqualTo(101L);
        }

        @Test
        void should_store_cycle_timestamp_when_created_via_factory() {
            var ts = Instant.parse("2026-03-15T09:30:00Z");
            var position = TrajectoryPosition.from("BA123", 0.0f, 0.0f, 10000.0f, 0.0f, 90, null, null, 50L, ts);

            assertThat(position.cycleTimestamp()).isEqualTo(ts);
        }

        @Test
        void should_store_heading_deg_when_created_via_factory() {
            var position = TrajectoryPosition.from("BA123", 0.0f, 0.0f, 10000.0f, 0.0f, 270, null, null, 1L, CYCLE_TIME);

            assertThat(position.headingDeg()).isEqualTo(270);
        }

        @Test
        void should_widen_float_lat_to_double_when_present() {
            var position = TrajectoryPosition.from("BA123", 0.0f, 0.0f, 10000.0f, 0.0f, 90, 51.5f, -0.12f, 1L, CYCLE_TIME);

            assertThat(position.lat()).isEqualTo((double) 51.5f);
            assertThat(position.lon()).isEqualTo((double) -0.12f);
        }

        @Test
        void should_store_null_lat_and_lon_when_absent() {
            var position = TrajectoryPosition.from("BA123", 0.0f, 0.0f, 10000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);

            assertThat(position.lat()).isNull();
            assertThat(position.lon()).isNull();
        }

        @Test
        void should_propagate_speed_knots_to_domain_record_from_float_radar_message_fields() {
            var position = TrajectoryPosition.from("BA123", 0.0f, 0.0f, 10000.0f, 250.5f, 90, null, null, 42L, CYCLE_TIME);

            assertThat(position.speedKn()).isEqualTo((double) 250.5f);
        }

        @Test
        void should_reject_negative_speed_when_constructing_trajectory_position() {
            assertThatThrownBy(() -> TrajectoryPosition.from("BA123", 0.0f, 0.0f, 10000.0f, -1.0f, 90, null, null, 42L, CYCLE_TIME))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("speedKn");
        }
    }

    @Nested
    class Equality {

        @Test
        void should_be_equal_when_all_components_are_identical() {
            var posA = TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);
            var posB = TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);

            assertThat(posA).isEqualTo(posB);
        }

        @Test
        void should_not_be_equal_when_callsigns_differ() {
            var posA = TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);
            var posB = TrajectoryPosition.from("AF456", 1000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);

            assertThat(posA).isNotEqualTo(posB);
        }

        @Test
        void should_not_be_equal_when_positions_differ() {
            var posA = TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);
            var posB = TrajectoryPosition.from("BA123", 5000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);

            assertThat(posA).isNotEqualTo(posB);
        }

        @Test
        void should_not_be_equal_when_radar_cycles_differ() {
            var posA = TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);
            var posB = TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 2L, CYCLE_TIME);

            assertThat(posA).isNotEqualTo(posB);
        }

        @Test
        void should_not_be_equal_when_heading_deg_differs() {
            var posA = TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 15000.0f, 0.0f, 90, null, null, 1L, CYCLE_TIME);
            var posB = TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 15000.0f, 0.0f, 180, null, null, 1L, CYCLE_TIME);

            assertThat(posA).isNotEqualTo(posB);
        }
    }

    @Nested
    class Validation {

        private static final Instant T = Instant.parse("2026-03-01T10:00:00Z");

        @Test
        void should_throw_when_callsign_is_null() {
            assertThatThrownBy(() -> new TrajectoryPosition(null, 1.0, 2.0, 3000.0, 0.0, 90, null, null, 1L, T))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void should_throw_when_callsign_is_blank() {
            assertThatThrownBy(() -> new TrajectoryPosition("   ", 1.0, 2.0, 3000.0, 0.0, 90, null, null, 1L, T))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void should_throw_when_altitude_is_NaN() {
            assertThatThrownBy(() -> new TrajectoryPosition("BA123", 1.0, 2.0, Double.NaN, 0.0, 90, null, null, 1L, T))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("finite");
        }

        @Test
        void should_throw_when_x_is_positive_infinity() {
            assertThatThrownBy(() -> new TrajectoryPosition("BA123", Double.POSITIVE_INFINITY, 2.0, 3000.0, 0.0, 90, null, null, 1L, T))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("finite");
        }

        @Test
        void should_throw_when_y_is_negative_infinity() {
            assertThatThrownBy(() -> new TrajectoryPosition("BA123", 1.0, Double.NEGATIVE_INFINITY, 3000.0, 0.0, 90, null, null, 1L, T))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("finite");
        }

        @Test
        void should_throw_when_altitude_is_positive_infinity() {
            assertThatThrownBy(() -> new TrajectoryPosition("BA123", 1.0, 2.0, Double.POSITIVE_INFINITY, 0.0, 90, null, null, 1L, T))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("finite");
        }

        @Test
        void should_throw_when_cycleTimestamp_is_null() {
            assertThatThrownBy(() -> new TrajectoryPosition("BA123", 1.0, 2.0, 3000.0, 0.0, 90, null, null, 1L, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void should_create_successfully_when_all_inputs_valid() {
            assertThatNoException().isThrownBy(
                    () -> new TrajectoryPosition("BA123", 1.0, 2.0, 3000.0, 0.0, 90, null, null, 1L, T));
        }
    }
}
