package com.atcsafety.radar.application;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RadarConfig}.
 *
 * <p>These tests verify the record-level contract: that the values passed to the
 * constructor are accessible via the corresponding accessors. No Spring context needed.
 */
class RadarConfigTest {

    @Nested
    class Construction {

        @Test
        void should_hold_lat_and_lon_values_when_constructed() {
            RadarConfig config = new RadarConfig(48.8566, 2.3522);

            assertThat(config.lat()).isEqualTo(48.8566);
            assertThat(config.lon()).isEqualTo(2.3522);
        }

        @Test
        void should_hold_negative_coordinates_when_constructed_with_southern_and_western_values() {
            RadarConfig config = new RadarConfig(-33.8688, -70.6693);

            assertThat(config.lat()).isEqualTo(-33.8688);
            assertThat(config.lon()).isEqualTo(-70.6693);
        }
    }
}
