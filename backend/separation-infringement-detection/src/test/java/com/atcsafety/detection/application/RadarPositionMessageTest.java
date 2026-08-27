package com.atcsafety.detection.application;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RadarPositionMessageTest {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @Nested
    class Deserialization {

        @Test
        void should_deserialize_all_fields_when_message_is_complete() throws Exception {
            var json = """
                    {
                      "targetId": 101,
                      "radarCycle": 5,
                      "flightNb": "BA123",
                      "x": 1500.0,
                      "y": 2500.0,
                      "altFeet": 28000.0,
                      "speedKn": 420.0,
                      "headingDeg": 135,
                      "lat": 51.5,
                      "lon": -0.12,
                      "timestampUTC": "2026-03-01T10:00:00Z"
                    }
                    """;
            var msg = MAPPER.readValue(json, RadarPositionMessage.class);

            assertThat(msg.targetId()).isEqualTo(101);
            assertThat(msg.radarCycle()).isEqualTo(5);
            assertThat(msg.flightNb()).isEqualTo("BA123");
            assertThat(msg.x()).isEqualTo(1500.0f);
            assertThat(msg.y()).isEqualTo(2500.0f);
            assertThat(msg.altFeet()).isEqualTo(28000.0f);
            assertThat(msg.speedKn()).isEqualTo(420.0f);
            assertThat(msg.headingDeg()).isEqualTo(135);
            assertThat(msg.lat()).isEqualTo(51.5f);
            assertThat(msg.lon()).isEqualTo(-0.12f);
            assertThat(msg.timestampUTC()).isEqualTo("2026-03-01T10:00:00Z");
        }

        @Test
        void should_not_throw_when_message_contains_unknown_fields() {
            var json = """
                    {
                      "targetId": 101,
                      "radarCycle": 5,
                      "flightNb": "BA123",
                      "x": 1500.0,
                      "y": 2500.0,
                      "altFeet": 28000.0,
                      "speedKn": 420.0,
                      "headingDeg": 90,
                      "timestampUTC": "2026-03-01T10:00:00Z",
                      "futureFieldFromSchemaV2": "ignored-by-this-consumer"
                    }
                    """;

            assertThatCode(() -> MAPPER.readValue(json, RadarPositionMessage.class))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_deserialize_lat_and_lon_as_null_when_absent_from_message() throws Exception {
            var json = """
                    {
                      "targetId": 101,
                      "radarCycle": 5,
                      "flightNb": "BA123",
                      "x": 1500.0,
                      "y": 2500.0,
                      "altFeet": 28000.0,
                      "speedKn": 420.0,
                      "headingDeg": 270,
                      "timestampUTC": "2026-03-01T10:00:00Z"
                    }
                    """;
            var msg = MAPPER.readValue(json, RadarPositionMessage.class);

            assertThat(msg.lat()).isNull();
            assertThat(msg.lon()).isNull();
        }

        @Test
        void should_default_heading_deg_to_zero_when_absent_from_message() throws Exception {
            var json = """
                    {
                      "targetId": 101,
                      "radarCycle": 5,
                      "flightNb": "BA123",
                      "x": 1500.0,
                      "y": 2500.0,
                      "altFeet": 28000.0,
                      "speedKn": 420.0,
                      "timestampUTC": "2026-03-01T10:00:00Z"
                    }
                    """;
            var msg = MAPPER.readValue(json, RadarPositionMessage.class);

            assertThat(msg.headingDeg()).isZero();
        }
    }

    @Nested
    class FlushSentinel {

        @Test
        void should_identify_message_as_flush_sentinel_when_flight_nb_matches_constant() {
            var flush = new RadarPositionMessage(
                    0, 6, RadarPositionMessage.FLUSH_FLIGHT_NB,
                    0f, 0f, 0f, 0f, 0, null, null, null);

            assertThat(flush.isFlushSentinel()).isTrue();
        }

        @Test
        void should_not_identify_regular_position_as_flush_sentinel() {
            var regular = new RadarPositionMessage(
                    101, 5, "BA123",
                    1500f, 2500f, 28000f, 420f, 135, null, null, "2026-03-01T10:00:00Z");

            assertThat(regular.isFlushSentinel()).isFalse();
        }

        @Test
        void should_not_identify_message_with_null_flight_nb_as_flush_sentinel() {
            var noCallsign = new RadarPositionMessage(
                    101, 5, null,
                    1500f, 2500f, 28000f, 420f, 0, null, null, "2026-03-01T10:00:00Z");

            assertThat(noCallsign.isFlushSentinel()).isFalse();
        }

        @Test
        void should_expose_flush_sentinel_constant_as_double_underscore_flush() {
            assertThat(RadarPositionMessage.FLUSH_FLIGHT_NB).isEqualTo("__FLUSH__");
        }
    }
}
