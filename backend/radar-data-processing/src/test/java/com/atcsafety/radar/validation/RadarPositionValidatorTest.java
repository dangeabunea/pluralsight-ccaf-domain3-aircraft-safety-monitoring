package com.atcsafety.radar.validation;

import com.atcsafety.contracts.RadarPosition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RadarPositionValidatorTest {

    private final RadarPositionValidator validator = new RadarPositionValidator();

    private static RadarPosition position(Float lat, Float lon, float altFeet) {
        return new RadarPosition(1, 1, 0f, 0f, altFeet, 400f, "2025-01-01T00:00:00Z", 90, "BA123", lat, lon);
    }

    private static String reasonOf(ValidationResult result) {
        return ((ValidationResult.Invalid) result).reason();
    }

    @Nested
    class Validate {

        @Test
        void should_return_invalid_mentioning_lat_when_lat_is_above_90() {
            // arrange
            var p = position(90.1f, 0f, 10000f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("lat=90.1 is outside [-90, 90]");
        }

        @Test
        void should_return_invalid_mentioning_lat_when_lat_is_below_minus_90() {
            // arrange
            var p = position(-90.1f, 0f, 10000f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("lat=-90.1 is outside [-90, 90]");
        }

        @Test
        void should_return_invalid_mentioning_lon_when_lon_is_above_180() {
            // arrange
            var p = position(0f, 180.1f, 10000f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("lon=180.1 is outside [-180, 180]");
        }

        @Test
        void should_return_invalid_mentioning_lon_when_lon_is_below_minus_180() {
            // arrange
            var p = position(0f, -180.1f, 10000f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("lon=-180.1 is outside [-180, 180]");
        }

        @Test
        void should_return_invalid_mentioning_altFeet_when_altitude_is_above_48000() {
            // arrange
            var p = position(0f, 0f, 48000.5f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("altFeet=48000.5 is outside [0, 48000]");
        }

        @Test
        void should_return_invalid_mentioning_altFeet_when_altitude_is_negative() {
            // arrange
            var p = position(0f, 0f, -0.5f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("altFeet=-0.5 is outside [0, 48000]");
        }

        @Test
        void should_return_invalid_mentioning_altFeet_when_altitude_is_NaN() {
            // arrange
            var p = position(0f, 0f, Float.NaN);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("altFeet=NaN is outside [0, 48000]");
        }

        @Test
        void should_return_invalid_mentioning_lat_when_lat_is_NaN() {
            // arrange
            var p = position(Float.NaN, 0f, 10000f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("lat=NaN is outside [-90, 90]");
        }

        @Test
        void should_return_valid_when_values_are_exactly_on_the_inclusive_bounds() {
            // arrange
            var lowerBounds = position(-90f, -180f, 0f);
            var upperBounds = position(90f, 180f, 48000f);

            // act
            var lowerResult = validator.validate(lowerBounds);
            var upperResult = validator.validate(upperBounds);

            // assert
            assertThat(lowerResult).isInstanceOf(ValidationResult.Valid.class);
            assertThat(upperResult).isInstanceOf(ValidationResult.Valid.class);
        }

        @Test
        void should_return_valid_when_lat_and_lon_are_null() {
            // arrange
            var p = position(null, null, 10000f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Valid.class);
        }

        @Test
        void should_report_all_failures_when_lat_lon_and_altitude_are_all_out_of_range() {
            // arrange
            var p = position(91f, 181f, 49000f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(reasonOf(result)).isEqualTo(
                    "lat=91.0 is outside [-90, 90]; "
                            + "lon=181.0 is outside [-180, 180]; "
                            + "altFeet=49000.0 is outside [0, 48000]");
        }

        @Test
        void should_return_only_flightNb_failure_when_flightNb_is_blank_and_other_fields_are_invalid() {
            // arrange
            var p = new RadarPosition(1, 1, 0f, 0f, 49000f, 400f, "2025-01-01T00:00:00Z", 90, " ", 91f, 181f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(reasonOf(result)).isEqualTo("flightNb is missing or blank");
        }

        @Test
        void should_return_invalid_mentioning_lon_when_lon_is_NaN() {
            // arrange
            var p = position(0f, Float.NaN, 10000f);

            // act
            var result = validator.validate(p);

            // assert
            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(reasonOf(result)).contains("lon=NaN is outside [-180, 180]");
        }
    }
}
