package com.atcsafety.radar.loading;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atcsafety.contracts.RadarPosition;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RadarDumpLoaderTest {

    private ObjectMapper objectMapper;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();

        logAppender = new ListAppender<>();
        logAppender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(RadarDumpLoader.class);
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(RadarDumpLoader.class);
        logger.detachAppender(logAppender);
    }

    @Nested
    class Load {

        @Test
        void should_deserialize_all_required_fields_when_record_is_valid(@TempDir Path tempDir)
                throws IOException {
            String json = """
                    [{
                      "targetId": 1,
                      "radarCycle": 3,
                      "x": -180000.0,
                      "y": 160000.0,
                      "altFeet": 38000.0,
                      "speedKn": 420.0,
                      "timestampUTC": "2026-03-20T10:00:15Z",
                      "headingDeg": 270,
                      "flightNb": "BAW447",
                      "lat": 52.283,
                      "lon": 1.889
                    }]
                    """;
            Path file = writeJson(tempDir, json);

            List<RadarPosition> positions = new RadarDumpLoader(objectMapper, file.toString()).load();

            assertThat(positions).hasSize(1);
            RadarPosition pos = positions.getFirst();
            assertThat(pos.targetId()).isEqualTo(1);
            assertThat(pos.radarCycle()).isEqualTo(3);
            assertThat(pos.x()).isEqualTo(-180000.0f);
            assertThat(pos.y()).isEqualTo(160000.0f);
            assertThat(pos.altFeet()).isEqualTo(38000.0f);
            assertThat(pos.speedKn()).isEqualTo(420.0f);
            assertThat(pos.timestampUTC()).isEqualTo("2026-03-20T10:00:15Z");
            assertThat(pos.headingDeg()).isEqualTo(270);
            assertThat(pos.flightNb()).isEqualTo("BAW447");
            assertThat(pos.lat()).isEqualTo(52.283f);
            assertThat(pos.lon()).isEqualTo(1.889f);
        }

        @Test
        void should_map_optional_fields_to_null_when_absent(@TempDir Path tempDir)
                throws IOException {
            String json = """
                    [{
                      "targetId": 2,
                      "radarCycle": 1,
                      "x": 10000.0,
                      "y": 5000.0,
                      "altFeet": 36000.0,
                      "speedKn": 420.0,
                      "timestampUTC": "2026-03-20T10:00:05Z",
                      "headingDeg": 90
                    }]
                    """;
            Path file = writeJson(tempDir, json);

            List<RadarPosition> positions = new RadarDumpLoader(objectMapper, file.toString()).load();

            assertThat(positions).hasSize(1);
            RadarPosition pos = positions.getFirst();
            assertThat(pos.flightNb()).isNull();
            assertThat(pos.lat()).isNull();
            assertThat(pos.lon()).isNull();
            assertThat(pos.targetId()).isEqualTo(2);
        }

        @Test
        void should_log_error_and_continue_when_record_has_type_mismatch(@TempDir Path tempDir)
                throws IOException {
            String json = """
                    [
                      {
                        "targetId": 1,
                        "radarCycle": 1,
                        "x": -180000.0,
                        "y": 160000.0,
                        "altFeet": 38000.0,
                        "speedKn": 420.0,
                        "timestampUTC": "2026-03-20T10:00:05Z",
                        "headingDeg": 180
                      },
                      {
                        "targetId": "NOT_AN_INT",
                        "radarCycle": 1,
                        "x": 0.0,
                        "y": 0.0,
                        "altFeet": 0.0,
                        "speedKn": 0.0,
                        "timestampUTC": "2026-03-20T10:00:05Z"
                      }
                    ]
                    """;
            Path file = writeJson(tempDir, json);

            List<RadarPosition> positions = new RadarDumpLoader(objectMapper, file.toString()).load();

            assertThat(positions).hasSize(1);
            assertThat(logAppender.list)
                    .anyMatch(event -> event.getLevel() == Level.ERROR);
        }

        @Test
        void should_log_error_and_continue_when_required_field_is_missing(@TempDir Path tempDir)
                throws IOException {
            String json = """
                    [{
                      "radarCycle": 1,
                      "x": 0.0,
                      "y": 0.0,
                      "altFeet": 0.0,
                      "speedKn": 0.0,
                      "timestampUTC": "2026-03-20T10:00:05Z"
                    }]
                    """;
            Path file = writeJson(tempDir, json);

            List<RadarPosition> positions = new RadarDumpLoader(objectMapper, file.toString()).load();

            assertThat(positions).isEmpty();
            assertThat(logAppender.list)
                    .anyMatch(event -> event.getLevel() == Level.ERROR);
        }

        @Test
        void should_throw_descriptive_exception_when_file_does_not_exist(@TempDir Path tempDir) {
            String missingPath = tempDir.resolve("nonexistent.json").toString();

            assertThatThrownBy(() -> new RadarDumpLoader(objectMapper, missingPath).load())
                    .isInstanceOf(RadarDumpFileNotFoundException.class)
                    .hasMessageContaining(missingPath);
        }

        @Test
        void should_return_empty_list_when_file_contains_empty_array(@TempDir Path tempDir)
                throws IOException {
            Path file = writeJson(tempDir, "[]");

            List<RadarPosition> positions = new RadarDumpLoader(objectMapper, file.toString()).load();

            assertThat(positions).isEmpty();
        }
    }

    @Nested
    class GroupByCycle {

        @Test
        void should_return_empty_map_when_input_is_empty() {
            RadarDumpLoader loader = new RadarDumpLoader(objectMapper, "irrelevant");

            TreeMap<Integer, List<RadarPosition>> result = loader.groupByCycle(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        void should_group_positions_into_single_batch_when_all_share_same_cycle() {
            RadarDumpLoader loader = new RadarDumpLoader(objectMapper, "irrelevant");
            List<RadarPosition> positions = List.of(pos(1, 2), pos(2, 2), pos(3, 2));

            TreeMap<Integer, List<RadarPosition>> result = loader.groupByCycle(positions);

            assertThat(result).hasSize(1);
            assertThat(result.get(2)).hasSize(3);
        }

        @Test
        void should_group_positions_into_distinct_batches_when_cycles_differ() {
            RadarDumpLoader loader = new RadarDumpLoader(objectMapper, "irrelevant");
            List<RadarPosition> positions = List.of(pos(1, 1), pos(2, 2), pos(3, 3));

            TreeMap<Integer, List<RadarPosition>> result = loader.groupByCycle(positions);

            assertThat(result).hasSize(3);
            assertThat(result.get(1)).hasSize(1);
            assertThat(result.get(2)).hasSize(1);
            assertThat(result.get(3)).hasSize(1);
        }

        @Test
        void should_return_batches_in_ascending_cycle_order_regardless_of_input_order() {
            RadarDumpLoader loader = new RadarDumpLoader(objectMapper, "irrelevant");
            // Deliberately out of order: 3, 1, 2, 1, 3
            List<RadarPosition> positions = List.of(
                    pos(1, 3), pos(2, 1), pos(3, 2), pos(4, 1), pos(5, 3));

            TreeMap<Integer, List<RadarPosition>> result = loader.groupByCycle(positions);

            assertThat(result.keySet()).containsExactly(1, 2, 3);
        }

        @Test
        void should_group_normally_when_radarCycle_is_zero() {
            RadarDumpLoader loader = new RadarDumpLoader(objectMapper, "irrelevant");
            List<RadarPosition> positions = List.of(pos(1, 0));

            TreeMap<Integer, List<RadarPosition>> result = loader.groupByCycle(positions);

            assertThat(result).containsKey(0);
            assertThat(result.get(0)).hasSize(1);
        }

        @Test
        void should_pass_through_positions_with_null_flightNb_unchanged() {
            RadarDumpLoader loader = new RadarDumpLoader(objectMapper, "irrelevant");
            RadarPosition nullFlightNb = pos(1, 5);

            TreeMap<Integer, List<RadarPosition>> result = loader.groupByCycle(List.of(nullFlightNb));

            assertThat(result.get(5)).hasSize(1);
            assertThat(result.get(5).getFirst().flightNb()).isNull();
        }

        @Test
        void should_handle_non_contiguous_cycle_values() {
            RadarDumpLoader loader = new RadarDumpLoader(objectMapper, "irrelevant");
            List<RadarPosition> positions = List.of(pos(1, 1), pos(2, 3), pos(3, 5));

            TreeMap<Integer, List<RadarPosition>> result = loader.groupByCycle(positions);

            assertThat(result).hasSize(3);
            assertThat(result.keySet()).containsExactly(1, 3, 5);
        }

        @Test
        void should_assign_positions_to_correct_cycle_buckets() {
            RadarDumpLoader loader = new RadarDumpLoader(objectMapper, "irrelevant");
            RadarPosition a = pos(10, 1);
            RadarPosition b = pos(20, 1);
            RadarPosition c = pos(30, 2);

            TreeMap<Integer, List<RadarPosition>> result = loader.groupByCycle(List.of(a, b, c));

            assertThat(result.get(1)).containsExactlyInAnyOrder(a, b);
            assertThat(result.get(2)).containsExactly(c);
        }
    }

    private static Path writeJson(Path dir, String json) throws IOException {
        Path file = dir.resolve("radar-dump.json");
        Files.writeString(file, json);
        return file;
    }

    private static RadarPosition pos(int targetId, int radarCycle) {
        return new RadarPosition(targetId, radarCycle, 0f, 0f, 0f, 0f,
                "2026-01-01T00:00:00Z", 0, null, null, null);
    }
}
