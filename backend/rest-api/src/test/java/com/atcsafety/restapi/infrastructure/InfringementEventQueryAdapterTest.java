package com.atcsafety.restapi.infrastructure;

import com.atcsafety.restapi.application.CommentData;
import com.atcsafety.restapi.application.InfringementEventData;
import com.atcsafety.restapi.application.InfringementEventDetailData;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link InfringementEventQueryAdapter}.
 *
 * <p>Uses a real MongoDB instance via Testcontainers. {@code @DataMongoTest} loads
 * only the MongoDB slice — no Kafka or web layer required. {@code @ServiceConnection}
 * wires the container's connection details automatically.
 */
@DataMongoTest
@Testcontainers
@Import(InfringementEventQueryAdapter.class)
class InfringementEventQueryAdapterTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:latest");

    @Autowired
    private InfringementEventQueryAdapter adapter;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String COLLECTION = "separation_infringement_events";
    private static final Instant BASE_TIME = Instant.parse("2026-01-01T10:00:00Z");

    @BeforeEach
    void clearCollection() {
        mongoTemplate.dropCollection(COLLECTION);
    }

    @Nested
    class CountWithMongoStatuses {

        @Test
        void should_count_documents_matching_given_statuses() {
            insertEvent("CLOSED", BASE_TIME, null);
            insertEvent("CLOSED", BASE_TIME, null);
            insertEvent("ESCALATED", BASE_TIME, Instant.parse("2026-01-02T10:00:00Z"));

            long pending = adapter.countWithMongoStatuses(List.of("CLOSED", "PENDING_REVIEW"));

            assertThat(pending).isEqualTo(2L);
        }

        @Test
        void should_return_zero_when_no_documents_match_given_statuses() {
            insertEvent("DISMISSED", BASE_TIME, null);

            long pending = adapter.countWithMongoStatuses(List.of("CLOSED", "PENDING_REVIEW"));

            assertThat(pending).isZero();
        }
    }

    @Nested
    class FindPageWithMongoStatuses {

        @Test
        void should_return_only_documents_matching_given_statuses() {
            insertEvent("CLOSED", BASE_TIME, null);
            insertEvent("ESCALATED", BASE_TIME, Instant.parse("2026-01-02T10:00:00Z"));
            insertEvent("DISMISSED", BASE_TIME, null);

            List<InfringementEventData> page = adapter.findPageWithMongoStatuses(
                    List.of("CLOSED"), 0, 20, null, null);

            assertThat(page).hasSize(1);
            assertThat(page.get(0).mongoStatus()).isEqualTo("CLOSED");
        }

        @Test
        void should_return_page_of_documents_sorted_by_start_time_ascending() {
            Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
            Instant later = Instant.parse("2026-01-02T00:00:00Z");
            insertEvent("CLOSED", later, null);
            insertEvent("CLOSED", earlier, null);

            List<InfringementEventData> page = adapter.findPageWithMongoStatuses(
                    List.of("CLOSED"), 0, 20, "startedAt", "asc");

            assertThat(page).hasSize(2);
            assertThat(page.get(0).startedAt()).isEqualTo(earlier);
            assertThat(page.get(1).startedAt()).isEqualTo(later);
        }

        @Test
        void should_return_page_of_documents_sorted_by_escalated_at_ascending() {
            Instant ea1 = Instant.parse("2026-01-01T10:00:00Z");
            Instant ea2 = Instant.parse("2026-01-02T10:00:00Z");
            insertEvent("ESCALATED", BASE_TIME, ea2);
            insertEvent("ESCALATED", BASE_TIME, ea1);

            List<InfringementEventData> page = adapter.findPageWithMongoStatuses(
                    List.of("ESCALATED"), 0, 20, "escalatedAt", "asc");

            assertThat(page).hasSize(2);
            assertThat(page.get(0).escalatedAt()).isEqualTo(ea1);
            assertThat(page.get(1).escalatedAt()).isEqualTo(ea2);
        }
    }

    @Nested
    class FindById {

        @Test
        void should_return_empty_when_no_event_exists_for_id() {
            Optional<InfringementEventDetailData> result = adapter.findById("non-existent-id");

            assertThat(result).isEmpty();
        }

        @Test
        void should_return_event_detail_with_trajectory_positions_when_event_exists() {
            String id = insertEventWithTrajectory("CLOSED", BASE_TIME);

            Optional<InfringementEventDetailData> result = adapter.findById(id);

            assertThat(result).isPresent();
            InfringementEventDetailData detail = result.get();
            assertThat(detail.mongoStatus()).isEqualTo("CLOSED");
            assertThat(detail.trajectory1()).hasSize(1);
            assertThat(detail.trajectory1().get(0).radarCycle()).isEqualTo(1L);
            assertThat(detail.trajectory1().get(0).lat()).isEqualTo(48.946);
            assertThat(detail.trajectory1().get(0).lon()).isEqualTo(2.442);
            assertThat(detail.trajectory2()).hasSize(1);
        }

        @Test
        void should_return_event_detail_with_min_separation_cycle_index_when_set() {
            String id = insertEventWithMinSeparationIndex("CLOSED", BASE_TIME, 3);

            Optional<InfringementEventDetailData> result = adapter.findById(id);

            assertThat(result).isPresent();
            assertThat(result.get().minSeparationCycleIndex()).isEqualTo(3);
        }

        @Test
        void should_return_event_start_and_end_cycle_when_written_by_safm_76_detection_service() {
            String id = insertEventWithEventBounds("CLOSED", BASE_TIME, 3, 7);

            Optional<InfringementEventDetailData> result = adapter.findById(id);

            assertThat(result).isPresent();
            assertThat(result.get().eventStartCycle()).isEqualTo(3);
            assertThat(result.get().eventEndCycle()).isEqualTo(7);
        }

        @Test
        void should_return_null_event_start_and_end_cycle_for_pre_safm_76_events() {
            // Legacy events written before SAFM-76 have no eventStartCycle / eventEndCycle field
            String id = insertEventWithMinSeparationIndex("CLOSED", BASE_TIME, 2);

            Optional<InfringementEventDetailData> result = adapter.findById(id);

            assertThat(result).isPresent();
            assertThat(result.get().eventStartCycle()).isNull();
            assertThat(result.get().eventEndCycle()).isNull();
        }
    }

    private void insertEvent(String status, Instant startTime, Instant escalatedAt) {
        Document doc = new Document()
                .append("firstAircraftCallsign", "AF100")
                .append("secondAircraftCallsign", "BA200")
                .append("status", status)
                .append("startedAt", Date.from(startTime))
                .append("startCycle", 100L);
        if (escalatedAt != null) {
            doc.append("escalatedAt", Date.from(escalatedAt));
        }
        mongoTemplate.getCollection(COLLECTION).insertOne(doc);
    }

    /**
     * Inserts an event with one trajectory position per aircraft and returns the generated id.
     */
    private String insertEventWithTrajectory(String status, Instant startTime) {
        Document position1 = new Document()
                .append("callsign", "AF100")
                .append("x", 60000.0)
                .append("y", 100000.0)
                .append("altFeet", 20000.0)
                .append("radarCycle", 1L)
                .append("cycleTimestamp", Date.from(startTime))
                .append("lat", 48.946)
                .append("lon", 2.442);
        Document position2 = new Document()
                .append("callsign", "BA200")
                .append("x", 55000.0)
                .append("y", 98000.0)
                .append("altFeet", 21000.0)
                .append("radarCycle", 1L)
                .append("cycleTimestamp", Date.from(startTime))
                .append("lat", 48.942)
                .append("lon", 2.436);
        Document doc = new Document()
                .append("firstAircraftCallsign", "AF100")
                .append("secondAircraftCallsign", "BA200")
                .append("status", status)
                .append("startedAt", Date.from(startTime))
                .append("startCycle", 1L)
                .append("trajectory1", Arrays.asList(position1))
                .append("trajectory2", Arrays.asList(position2));
        mongoTemplate.getCollection(COLLECTION).insertOne(doc);
        return doc.getObjectId("_id").toHexString();
    }

    private String insertEventWithMinSeparationIndex(String status, Instant startTime, int cycleIndex) {
        Document doc = new Document()
                .append("firstAircraftCallsign", "AF100")
                .append("secondAircraftCallsign", "BA200")
                .append("status", status)
                .append("startedAt", Date.from(startTime))
                .append("startCycle", 1L)
                .append("minSeparationCycleIndex", cycleIndex);
        mongoTemplate.getCollection(COLLECTION).insertOne(doc);
        return doc.getObjectId("_id").toHexString();
    }

    /**
     * Inserts an event with eventStartCycle and eventEndCycle as written by the SAFM-76
     * detection service (post-observation-window events).
     */
    private String insertEventWithEventBounds(String status, Instant startTime,
                                              int eventStartCycle, int eventEndCycle) {
        Document doc = new Document()
                .append("firstAircraftCallsign", "AF100")
                .append("secondAircraftCallsign", "BA200")
                .append("status", status)
                .append("startedAt", Date.from(startTime))
                .append("startCycle", 1L)
                .append("eventStartCycle", eventStartCycle)
                .append("eventEndCycle", eventEndCycle);
        mongoTemplate.getCollection(COLLECTION).insertOne(doc);
        return doc.getObjectId("_id").toHexString();
    }

    @Nested
    class ChangeStatus {

        @Test
        void should_update_status_and_dismissed_at_when_dismissing_event() {
            String id = insertEventReturningId("PENDING_REVIEW", BASE_TIME, null);
            Instant dismissedAt = Instant.parse("2026-03-19T11:00:00Z");

            adapter.changeStatus(id, "DISMISSED", null, null, dismissedAt);

            Optional<InfringementEventDetailData> result = adapter.findById(id);
            assertThat(result).isPresent();
            assertThat(result.get().mongoStatus()).isEqualTo("DISMISSED");
            assertThat(result.get().dismissedAt()).isEqualTo(dismissedAt);
            assertThat(result.get().escalatedAt()).isNull();
            assertThat(result.get().escalationReason()).isNull();
        }

        @Test
        void should_update_status_escalation_reason_and_escalated_at_when_escalating_event() {
            String id = insertEventReturningId("PENDING_REVIEW", BASE_TIME, null);
            Instant escalatedAt = Instant.parse("2026-03-19T11:05:00Z");

            adapter.changeStatus(id, "ESCALATED", "Near-miss at FL290", escalatedAt, null);

            Optional<InfringementEventDetailData> result = adapter.findById(id);
            assertThat(result).isPresent();
            assertThat(result.get().mongoStatus()).isEqualTo("ESCALATED");
            assertThat(result.get().escalatedAt()).isEqualTo(escalatedAt);
            assertThat(result.get().escalationReason()).isEqualTo("Near-miss at FL290");
            assertThat(result.get().dismissedAt()).isNull();
        }

        @Test
        void should_return_true_when_event_found_and_updated() {
            String id = insertEventReturningId("PENDING_REVIEW", BASE_TIME, null);

            boolean result = adapter.changeStatus(id, "DISMISSED", null, null,
                    Instant.parse("2026-03-19T11:00:00Z"));

            assertThat(result).isTrue();
        }

        @Test
        void should_return_false_when_event_not_found() {
            boolean result = adapter.changeStatus("nonexistent-id", "DISMISSED", null, null,
                    Instant.parse("2026-03-19T11:00:00Z"));

            assertThat(result).isFalse();
        }
    }

    @Nested
    class AddComment {

        @Test
        void should_return_true_and_append_comment_when_event_exists() {
            String id = insertEventReturningId("CLOSED", BASE_TIME, null);
            CommentData comment = new CommentData("uuid-1", "Test comment", "J. Smith", BASE_TIME);

            boolean found = adapter.addComment(id, comment);

            assertThat(found).isTrue();
        }

        @Test
        void should_return_false_when_event_does_not_exist() {
            CommentData comment = new CommentData("uuid-1", "Test comment", "J. Smith", BASE_TIME);

            boolean found = adapter.addComment("nonexistent-id", comment);

            assertThat(found).isFalse();
        }

        @Test
        void should_persist_comment_so_it_appears_in_subsequent_event_detail_query() {
            String id = insertEventReturningId("CLOSED", BASE_TIME, null);
            CommentData comment = new CommentData("uuid-1", "Reviewed — TCAS RA observed", "J. Smith", BASE_TIME);

            adapter.addComment(id, comment);

            Optional<InfringementEventDetailData> detail = adapter.findById(id);
            assertThat(detail).isPresent();
            assertThat(detail.get().comments()).hasSize(1);
            assertThat(detail.get().comments().get(0).id()).isEqualTo("uuid-1");
            assertThat(detail.get().comments().get(0).text()).isEqualTo("Reviewed — TCAS RA observed");
        }
    }

    private String insertEventReturningId(String status, Instant startTime, Instant escalatedAt) {
        Document doc = new Document()
                .append("firstAircraftCallsign", "AF100")
                .append("secondAircraftCallsign", "BA200")
                .append("status", status)
                .append("startedAt", Date.from(startTime))
                .append("startCycle", 100L);
        if (escalatedAt != null) {
            doc.append("escalatedAt", Date.from(escalatedAt));
        }
        mongoTemplate.getCollection(COLLECTION).insertOne(doc);
        return doc.getObjectId("_id").toHexString();
    }
}
