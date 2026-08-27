package com.atcsafety.radar.application;

import com.atcsafety.contracts.RadarPosition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RadarPositionMessageTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Nested
    class SerializeToJson {

        @Test
        void should_contain_all_required_fields_when_serialized() throws Exception {
            RadarPositionMessage msg = new RadarPositionMessage(
                    1, 1, "AF3455", 60000.0f, 100000.0f, 20000.0f, 456.0f, 135,
                    48.946f, 2.442f, "2026-03-19T10:00:00Z"
            );

            JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(msg));

            assertThat(json.has("targetId")).isTrue();
            assertThat(json.has("radarCycle")).isTrue();
            assertThat(json.has("flightNb")).isTrue();
            assertThat(json.has("x")).isTrue();
            assertThat(json.has("y")).isTrue();
            assertThat(json.has("altFeet")).isTrue();
            assertThat(json.has("speedKn")).isTrue();
            assertThat(json.has("headingDeg")).isTrue();
            assertThat(json.has("lat")).isTrue();
            assertThat(json.has("lon")).isTrue();
            assertThat(json.has("timestampUTC")).isTrue();
        }

        @Test
        void should_produce_exactly_11_fields_when_serialized() throws Exception {
            RadarPositionMessage msg = new RadarPositionMessage(
                    1, 1, "AF3455", 60000.0f, 100000.0f, 20000.0f, 456.0f, 135,
                    48.946f, 2.442f, "2026-03-19T10:00:00Z"
            );

            JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(msg));

            assertThat(json.size()).isEqualTo(11);
        }

        @Test
        void should_serialize_null_lat_and_lon_as_json_null() throws Exception {
            RadarPositionMessage msg = new RadarPositionMessage(
                    1, 1, "AF3455", 60000.0f, 100000.0f, 20000.0f, 456.0f, 270,
                    null, null, "2026-03-19T10:00:00Z"
            );

            String json = MAPPER.writeValueAsString(msg);

            assertThat(json).contains("\"lat\":null");
            assertThat(json).contains("\"lon\":null");
        }

        @Test
        void should_map_all_fields_from_radar_position() throws Exception {
            RadarPosition position = new RadarPosition(
                    42, 3, 10000.0f, 20000.0f, 35000.0f, 450.0f,
                    "2026-03-24T10:00:00Z", 90, "AF1234", 48.5f, 2.2f
            );

            RadarPositionMessage msg = new RadarPositionMessage(
                    position.targetId(),
                    position.radarCycle(),
                    position.flightNb(),
                    position.x(),
                    position.y(),
                    position.altFeet(),
                    position.speedKn(),
                    position.headingDeg(),
                    position.lat(),
                    position.lon(),
                    position.timestampUTC()
            );

            assertThat(msg.targetId()).isEqualTo(42);
            assertThat(msg.radarCycle()).isEqualTo(3);
            assertThat(msg.flightNb()).isEqualTo("AF1234");
            assertThat(msg.x()).isEqualTo(10000.0f);
            assertThat(msg.y()).isEqualTo(20000.0f);
            assertThat(msg.altFeet()).isEqualTo(35000.0f);
            assertThat(msg.speedKn()).isEqualTo(450.0f);
            assertThat(msg.headingDeg()).isEqualTo(90);
            assertThat(msg.lat()).isEqualTo(48.5f);
            assertThat(msg.lon()).isEqualTo(2.2f);
            assertThat(msg.timestampUTC()).isEqualTo("2026-03-24T10:00:00Z");
        }
    }
}
