package com.atcsafety.radar.enrichment;

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
import static org.assertj.core.api.Assertions.within;

class CoordinateDeriverTest {

    // Paris CDG-area radar installation — coordinates from the SAFM-14 ticket scenario
    private static final double RADAR_LAT = 48.8566;
    private static final double RADAR_LON = 2.3522;

    // Expected derived values for x=10000, y=5000 at the above radar installation.
    // x and y are in metres; 111320 m/degree is the equirectangular approximation constant.
    //   lat = 48.8566 + (5000 / 111320)                          ≈ 48.9015
    //   lon = 2.3522  + (10000 / (111320 * cos(48.8566°)))       ≈ 2.4888
    private static final float EXPECTED_LAT = 48.9015f;
    private static final float EXPECTED_LON = 2.4888f;
    private static final float TOLERANCE_DEGREES = 0.001f;

    private CoordinateDeriver deriver;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        deriver = new CoordinateDeriver(RADAR_LAT, RADAR_LON);

        logAppender = new ListAppender<>();
        logAppender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(CoordinateDeriver.class);
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(CoordinateDeriver.class);
        logger.detachAppender(logAppender);
    }

    // ---- Construction ----

    @Nested
    class Construction {

        @Test
        void should_throw_when_lat_exceeds_upper_bound() {
            assertThatThrownBy(() -> new CoordinateDeriver(90.1, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("90.1");
        }

        @Test
        void should_throw_when_lat_is_below_lower_bound() {
            assertThatThrownBy(() -> new CoordinateDeriver(-90.1, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-90.1");
        }

        @Test
        void should_throw_when_lon_exceeds_upper_bound() {
            assertThatThrownBy(() -> new CoordinateDeriver(0.0, 180.1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("180.1");
        }

        @Test
        void should_throw_when_lon_is_below_lower_bound() {
            assertThatThrownBy(() -> new CoordinateDeriver(0.0, -180.1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("-180.1");
        }

        @Test
        void should_throw_when_lat_is_NaN() {
            assertThatThrownBy(() -> new CoordinateDeriver(Double.NaN, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_throw_when_lon_is_infinite() {
            assertThatThrownBy(() -> new CoordinateDeriver(0.0, Double.POSITIVE_INFINITY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_accept_boundary_values() {
            // Boundary values must not throw
            new CoordinateDeriver(90.0, 180.0);
            new CoordinateDeriver(-90.0, -180.0);
            new CoordinateDeriver(0.0, 0.0);
        }
    }

    // ---- Derivation ----

    @Nested
    class DeriveCoordinates {

        // AC1 — both lat and lon absent

        @Test
        void should_derive_both_coords_when_both_absent() {
            RadarPosition position = positionWith(null, null);

            RadarPosition result = deriver.derive(position);

            assertThat(result.lat()).isCloseTo(EXPECTED_LAT, within(TOLERANCE_DEGREES));
            assertThat(result.lon()).isCloseTo(EXPECTED_LON, within(TOLERANCE_DEGREES));
        }

        @Test
        void should_log_warning_for_each_absent_coord_when_both_absent() {
            RadarPosition position = positionWith(null, null);

            deriver.derive(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .hasSize(2)
                    .anySatisfy(msg -> {
                        assertThat(msg).contains(String.valueOf(position.targetId()));
                        assertThat(msg).contains("lat");
                    })
                    .anySatisfy(msg -> {
                        assertThat(msg).contains(String.valueOf(position.targetId()));
                        assertThat(msg).contains("lon");
                    });
        }

        // AC2 — lat present, lon absent

        @Test
        void should_derive_only_lon_when_lat_present_and_lon_absent() {
            RadarPosition position = positionWith(48.8566f, null);

            RadarPosition result = deriver.derive(position);

            assertThat(result.lat()).isEqualTo(48.8566f);
            assertThat(result.lon()).isCloseTo(EXPECTED_LON, within(TOLERANCE_DEGREES));
        }

        @Test
        void should_log_warning_with_targetId_when_lon_absent() {
            RadarPosition position = positionWith(48.8566f, null);

            deriver.derive(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .hasSize(1)
                    .anySatisfy(msg -> {
                        assertThat(msg).contains(String.valueOf(position.targetId()));
                        assertThat(msg).contains("lon");
                    });
        }

        // AC3 — both present

        @Test
        void should_not_modify_coords_when_both_present() {
            RadarPosition position = positionWith(48.8566f, 2.3522f);

            RadarPosition result = deriver.derive(position);

            assertThat(result.lat()).isEqualTo(48.8566f);
            assertThat(result.lon()).isEqualTo(2.3522f);
        }

        @Test
        void should_not_log_warning_when_both_coords_present() {
            RadarPosition position = positionWith(48.8566f, 2.3522f);

            deriver.derive(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .isEmpty();
        }

        // AC4 (symmetric) — lon present, lat absent

        @Test
        void should_derive_only_lat_when_lon_present_and_lat_absent() {
            RadarPosition position = positionWith(null, 2.3522f);

            RadarPosition result = deriver.derive(position);

            assertThat(result.lat()).isCloseTo(EXPECTED_LAT, within(TOLERANCE_DEGREES));
            assertThat(result.lon()).isEqualTo(2.3522f);
        }

        @Test
        void should_log_warning_with_targetId_when_lat_absent() {
            RadarPosition position = positionWith(null, 2.3522f);

            deriver.derive(position);

            assertThat(logAppender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .hasSize(1)
                    .anySatisfy(msg -> {
                        assertThat(msg).contains(String.valueOf(position.targetId()));
                        assertThat(msg).contains("lat");
                    });
        }

        // Zero-offset case — verifies the radar origin is applied correctly

        @Test
        void should_derive_radar_installation_coords_when_offsets_are_zero() {
            RadarPosition position = new RadarPosition(
                    42, 1, 0.0f, 0.0f,
                    35000.0f, 450.0f, "2026-03-23T10:00:00Z", 0, "AF1234",
                    null, null
            );

            RadarPosition result = deriver.derive(position);

            assertThat(result.lat()).isCloseTo((float) RADAR_LAT, within(TOLERANCE_DEGREES));
            assertThat(result.lon()).isCloseTo((float) RADAR_LON, within(TOLERANCE_DEGREES));
        }

        // Negative-offset case — verifies sign is correct in the formula

        @Test
        void should_derive_coords_south_and_west_of_radar_when_offsets_are_negative() {
            RadarPosition position = new RadarPosition(
                    42, 1, -10000.0f, -5000.0f,
                    35000.0f, 450.0f, "2026-03-23T10:00:00Z", 0, "AF1234",
                    null, null
            );

            RadarPosition result = deriver.derive(position);

            assertThat(result.lat()).isLessThan((float) RADAR_LAT);
            assertThat(result.lon()).isLessThan((float) RADAR_LON);
        }

        // Regression guard — all non-coord fields must survive a derive() call unchanged

        @Test
        void should_preserve_all_non_coord_fields_unchanged_after_derivation() {
            RadarPosition position = positionWith(null, null);

            RadarPosition result = deriver.derive(position);

            assertThat(result.targetId()).isEqualTo(position.targetId());
            assertThat(result.radarCycle()).isEqualTo(position.radarCycle());
            assertThat(result.x()).isEqualTo(position.x());
            assertThat(result.y()).isEqualTo(position.y());
            assertThat(result.altFeet()).isEqualTo(position.altFeet());
            assertThat(result.speedKn()).isEqualTo(position.speedKn());
            assertThat(result.headingDeg()).isEqualTo(position.headingDeg());
            assertThat(result.timestampUTC()).isEqualTo(position.timestampUTC());
            assertThat(result.flightNb()).isEqualTo(position.flightNb());
        }
    }

    // ---- helpers ----

    private static RadarPosition positionWith(Float lat, Float lon) {
        return new RadarPosition(
                42,             // targetId
                1,              // radarCycle
                10000.0f,       // x  — matches AC1 scenario (metres east of radar)
                5000.0f,        // y  — matches AC1 scenario (metres north of radar)
                35000.0f,       // altFeet
                450.0f,         // speedKn
                "2026-03-23T10:00:00Z", // timestampUTC
                0,              // headingDeg
                "AF1234",       // flightNb
                lat,
                lon
        );
    }
}
