package com.atcsafety.radar.validation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atcsafety.contracts.RadarPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RadarPositionValidatorTest {

    private RadarPositionValidator validator;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        validator = new RadarPositionValidator();

        logAppender = new ListAppender<>();
        logAppender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(RadarPositionValidator.class);
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(RadarPositionValidator.class);
        logger.detachAppender(logAppender);
    }

    @Nested
    class Validate {

        @Test
        void should_return_invalid_when_lat_exceeds_upper_bound() {
            RadarPosition position = positionWith(91.0f, 0.0f);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(((ValidationResult.Invalid) result).reason())
                    .contains("lat=")
                    .contains("91.0")
                    .doesNotEndWith(";");
        }

        @Test
        void should_return_invalid_when_lon_is_below_lower_bound() {
            RadarPosition position = positionWith(0.0f, -181.0f);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        }

        @Test
        void should_return_valid_when_lat_is_at_lower_boundary_and_lon_is_at_upper_boundary() {
            RadarPosition position = positionWith(-90.0f, 180.0f);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Valid.class);
        }

        @Test
        void should_return_valid_when_lat_and_lon_are_zero() {
            RadarPosition position = positionWith(0.0f, 0.0f);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Valid.class);
        }

        @Test
        void should_return_valid_when_lat_is_at_upper_boundary_and_lon_is_at_lower_boundary() {
            RadarPosition position = positionWith(90.0f, -180.0f);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Valid.class);
        }

        @Test
        void should_return_valid_when_lat_and_lon_are_null() {
            RadarPosition position = positionWith(null, null);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Valid.class);
        }

        @Test
        void should_log_warning_with_targetId_when_lat_is_out_of_range() {
            RadarPosition position = positionWith(91.0f, 0.0f);

            validator.validate(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(msg -> {
                        assertThat(msg).contains(String.valueOf(position.targetId()));
                        assertThat(msg).contains("91.0");
                    });
        }

        @Test
        void should_log_warning_with_targetId_when_lon_is_out_of_range() {
            RadarPosition position = positionWith(0.0f, -181.0f);

            validator.validate(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(msg -> {
                        assertThat(msg).contains(String.valueOf(position.targetId()));
                        assertThat(msg).contains("-181.0");
                    });
        }

        @Test
        void should_return_invalid_and_log_two_warnings_when_both_fields_are_out_of_range() {
            RadarPosition position = positionWith(91.0f, -181.0f);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .hasSize(2)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(msg -> assertThat(msg).contains("lat="))
                    .anySatisfy(msg -> assertThat(msg).contains("lon="));
        }

        @Test
        void should_return_invalid_when_only_lon_is_out_of_range() {
            RadarPosition position = positionWith(0.0f, 181.0f);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        }

        @Test
        void should_return_invalid_when_flightNb_is_null() {
            RadarPosition position = positionWithFlightNb(null);

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        }

        @Test
        void should_return_invalid_when_flightNb_is_empty() {
            RadarPosition position = positionWithFlightNb("");

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        }

        @Test
        void should_return_invalid_when_flightNb_is_blank_whitespace() {
            RadarPosition position = positionWithFlightNb("   ");

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Invalid.class);
        }

        @Test
        void should_return_valid_when_flightNb_is_present() {
            RadarPosition position = positionWithFlightNb("AF1234");

            ValidationResult result = validator.validate(position);

            assertThat(result).isInstanceOf(ValidationResult.Valid.class);
        }

        @Test
        void should_log_warning_with_targetId_when_flightNb_is_null() {
            RadarPosition position = positionWithFlightNb(null);

            validator.validate(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(msg -> assertThat(msg).contains(String.valueOf(position.targetId())));
        }

        @Test
        void should_log_warning_with_targetId_when_flightNb_is_empty() {
            RadarPosition position = positionWithFlightNb("");

            validator.validate(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(msg -> assertThat(msg).contains(String.valueOf(position.targetId())));
        }

        @Test
        void should_log_warning_with_targetId_when_flightNb_is_blank_whitespace() {
            RadarPosition position = positionWithFlightNb("   ");

            validator.validate(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(msg -> assertThat(msg).contains(String.valueOf(position.targetId())));
        }

        @Test
        void should_not_check_lat_lon_when_flightNb_is_null() {
            // Both lat AND lon are out of range — without the early return, 2 warnings would fire.
            // The flightNb check must short-circuit before lat/lon validation runs:
            // only one WARNING log should appear (for missing flightNb), not two.
            RadarPosition position = new RadarPosition(
                    42, 1, 10000.0f, 20000.0f, 35000.0f, 450.0f,
                    "2026-03-23T10:00:00Z", 0, null, 91.0f, 181.0f);

            validator.validate(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .hasSize(1);
        }
    }

    @Nested
    class InvalidConstruction {

        @Test
        void should_throw_when_reason_is_blank() {
            assertThatThrownBy(() -> ValidationResult.Invalid.of(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_throw_when_reason_is_whitespace_only() {
            assertThatThrownBy(() -> ValidationResult.Invalid.of("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- helpers ----

    private static RadarPosition positionWith(Float lat, Float lon) {
        return new RadarPosition(
                42,         // targetId
                1,          // radarCycle
                10000.0f,   // x
                20000.0f,   // y
                35000.0f,   // altFeet
                450.0f,     // speedKn
                "2026-03-23T10:00:00Z", // timestampUTC
                0,          // headingDeg
                "AF1234",   // flightNb
                lat,
                lon
        );
    }

    private static RadarPosition positionWithFlightNb(String flightNb) {
        return new RadarPosition(
                42,         // targetId
                1,          // radarCycle
                10000.0f,   // x
                20000.0f,   // y
                35000.0f,   // altFeet
                450.0f,     // speedKn
                "2026-03-23T10:00:00Z", // timestampUTC
                0,          // headingDeg
                flightNb,
                48.5f,      // lat — valid, so lat/lon check does not interfere
                2.2f        // lon — valid
        );
    }
}
