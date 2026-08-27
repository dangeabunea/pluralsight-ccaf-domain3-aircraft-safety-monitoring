package com.atcsafety.detection.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SeparationThresholds}.
 *
 * <p>The domain record is pure Java — no Spring context, no framework setup.
 * Tests run fast and are fully isolated.
 */
class SeparationThresholdsTest {

    @Nested
    class Construction {

        @Test
        void should_store_horizontal_threshold_in_nm() {
            var thresholds = new SeparationThresholds(5.0, 1000);

            assertThat(thresholds.horizontalThresholdNm()).isEqualTo(5.0);
        }

        @Test
        void should_store_vertical_threshold_in_ft() {
            var thresholds = new SeparationThresholds(5.0, 1000);

            assertThat(thresholds.verticalThresholdFt()).isEqualTo(1000);
        }

        @Test
        void should_reflect_custom_horizontal_threshold_when_configured_below_default() {
            var thresholds = new SeparationThresholds(3.0, 1000);

            assertThat(thresholds.horizontalThresholdNm()).isEqualTo(3.0);
        }

        @Test
        void should_reflect_custom_vertical_threshold_when_configured_above_default() {
            var thresholds = new SeparationThresholds(6.0, 3000);

            assertThat(thresholds.verticalThresholdFt()).isEqualTo(3000);
        }
    }

    @Nested
    class Equality {

        @Test
        void should_be_equal_when_both_thresholds_are_the_same() {
            var a = new SeparationThresholds(5.0, 1000);
            var b = new SeparationThresholds(5.0, 1000);

            assertThat(a).isEqualTo(b);
        }

        @Test
        void should_not_be_equal_when_horizontal_thresholds_differ() {
            var a = new SeparationThresholds(5.0, 1000);
            var b = new SeparationThresholds(3.0, 1000);

            assertThat(a).isNotEqualTo(b);
        }
    }
}
