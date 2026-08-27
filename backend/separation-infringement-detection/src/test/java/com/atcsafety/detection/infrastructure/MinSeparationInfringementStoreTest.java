package com.atcsafety.detection.infrastructure;

import com.atcsafety.detection.domain.AircraftPairKey;
import com.atcsafety.detection.domain.MinSeparationEventStatus;
import com.atcsafety.detection.domain.MinSeparationInfringementEvent;
import com.atcsafety.detection.domain.TrajectoryPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MinSeparationInfringementStore}.
 *
 * <p>Uses a real MongoDB instance via Testcontainers. {@code @DataMongoTest} loads
 * only the MongoDB slice — no Kafka broker required. {@code @ServiceConnection}
 * wires the container's connection details automatically (no {@code @DynamicPropertySource}).
 *
 * <p>SAFM-76: the new {@code eventStartCycle} and {@code eventEndCycle} fields are
 * tested in the {@link Save} nested class below.
 *
 * <h2>Test object factory</h2>
 * <p>All aggregate construction goes through the static helpers at the bottom of this
 * class. If {@link MinSeparationInfringementEvent#createNew} changes its signature,
 * only those helpers need updating.
 */
@DataMongoTest
@Testcontainers
@Import(MinSeparationInfringementStore.class)
class MinSeparationInfringementStoreTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:latest");

    @Autowired
    private MinSeparationInfringementStore eventStore;

    @Autowired
    private MinSeparationInfringementRepository repository;

    private static final Instant T1 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T10:00:10Z");

    @BeforeEach
    void clearCollection() {
        repository.deleteAll();
    }

    @Nested
    class Save {

        @Test
        void should_persist_event_to_mongodb_when_saved() {
            var event = closedEvent("BA123", "AF456", 100L);

            eventStore.save(event);

            assertThat(repository.findById(event.getEventId())).isPresent();
        }

        @Test
        void should_preserve_callsigns_when_persisted() {
            var event = closedEvent("BA123", "AF456", 100L);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getFirstAircraftCallsign()).isEqualTo("AF456");
            assertThat(doc.getSecondAircraftCallsign()).isEqualTo("BA123");
        }

        @Test
        void should_preserve_start_cycle_when_persisted() {
            var event = closedEvent("BA123", "AF456", 200L);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getStartCycle()).isEqualTo(200L);
        }

        @Test
        void should_preserve_status_as_closed_when_persisted() {
            var event = closedEvent("BA123", "AF456", 100L);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getStatus()).isEqualTo(MinSeparationEventStatus.CLOSED);
        }

        @Test
        void should_preserve_start_time_when_persisted() {
            var event = closedEvent("BA123", "AF456", 100L);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getStartedAt()).isEqualTo(T1);
        }

        @Test
        void should_preserve_end_time_as_last_active_cycle_timestamp_when_persisted() {
            var pair = AircraftPairKey.of("BA123", "AF456");
            var posA1 = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 100L, T1);
            var posB1 = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, 100L, T1);
            var event = MinSeparationInfringementEvent.createNew(pair, 100L, T1, 1, List.of(), List.of(), posA1, posB1, 3.5, 800.0);
            // Extend active at T2 — lastActiveCycleTimestamp moves to T2
            var posA2 = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 101L, T2);
            var posB2 = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, 101L, T2);
            event.extendActive(T2, posA2, posB2, 3.0, 750.0);
            // Grace period → observation window → close
            event.enterGracePeriod();
            event.decrementGracePeriod(); // counter = 0 (gracePeriodCycles=1)
            event.enterObservationWindow();
            event.close();

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getEndedAt()).isEqualTo(T2);
        }

        @Test
        void should_preserve_trajectory_entries_when_persisted() {
            var event = closedEvent("BA123", "AF456", 100L);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getTrajectory1()).hasSize(1);
            assertThat(doc.getTrajectory2()).hasSize(1);
        }

        @Test
        void should_preserve_trajectory_position_field_values_when_persisted() {
            var event = closedEvent("BA123", "AF456", 100L);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getTrajectory1()).hasSize(1);
            var entry = doc.getTrajectory1().get(0);
            assertThat(entry.getCallsign()).isEqualTo("AF456");   // callsign1 is lexicographically first
            assertThat(entry.getRadarCycle()).isEqualTo(100L);
            assertThat(entry.getCycleTimestamp()).isEqualTo(T1);
            assertThat(entry.getX()).isEqualTo(1000.0);
            assertThat(entry.getAltFeet()).isEqualTo(20000.0);
        }

        @Test
        void should_store_multiple_events_independently() {
            var event1 = closedEvent("BA123", "AF456", 100L);
            var event2 = closedEvent("KL007", "LH400", 200L);

            eventStore.save(event1);
            eventStore.save(event2);

            assertThat(repository.count()).isEqualTo(2);
        }

        @Test
        void should_preserve_lat_lon_and_heading_when_present_in_trajectory() {
            var pair = AircraftPairKey.of("BA123", "AF456");
            var posA = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 135, 51.5f, -0.12f, 100L, T1);
            var posB = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 270, 48.9f, 2.3f, 100L, T1);
            var event = MinSeparationInfringementEvent.createNew(pair, 100L, T1, 1, List.of(), List.of(), posA, posB, 3.5, 800.0);
            event.enterGracePeriod();
            event.decrementGracePeriod();
            event.enterObservationWindow();
            event.close();

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            // trajectory1 holds the lexicographically-first callsign (AF456 < BA123)
            // posA was built for callsign1 (AF456): heading=135, lat=51.5, lon=-0.12
            var entry1 = doc.getTrajectory1().get(0);
            assertThat(entry1.getHeadingDeg()).isEqualTo(135);
            assertThat(entry1.getLat()).isEqualTo((double) 51.5f);
            assertThat(entry1.getLon()).isEqualTo((double) -0.12f);

            // posB was built for callsign2 (BA123): heading=270, lat=48.9, lon=2.3
            var entry2 = doc.getTrajectory2().get(0);
            assertThat(entry2.getHeadingDeg()).isEqualTo(270);
            assertThat(entry2.getLat()).isEqualTo((double) 48.9f);
            assertThat(entry2.getLon()).isEqualTo((double) 2.3f);
        }

        @Test
        void should_preserve_null_lat_lon_when_absent_from_trajectory() {
            var event = closedEvent("BA123", "AF456", 100L);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            var entry = doc.getTrajectory1().get(0);
            assertThat(entry.getLat()).isNull();
            assertThat(entry.getLon()).isNull();
        }

        // SAFM-46: minimum-separation persistence

        @Test
        void should_persist_min_horizontal_separation_nm_when_event_is_saved() {
            var event = closedEventWithMinSep("BA123", "AF456", 100L, 2.5, 600.0);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getMinHorizontalSeparationNm()).isEqualTo(2.5);
        }

        @Test
        void should_persist_min_vertical_separation_ft_when_event_is_saved() {
            var event = closedEventWithMinSep("BA123", "AF456", 100L, 2.5, 600.0);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getMinVerticalSeparationFt()).isEqualTo(600.0);
        }

        @Test
        void should_persist_min_separation_cycle_index_as_zero_when_minimum_occurs_at_first_cycle_with_no_pre_event() {
            // Single-cycle event with no pre-event context: first (and only) cycle is by definition the minimum
            var event = closedEventWithMinSep("BA123", "AF456", 100L, 2.5, 600.0);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getMinSeparationCycleIndex()).isEqualTo(0);
        }

        @Test
        void should_persist_min_separation_cycle_index_pointing_to_cycle_with_minimum_horizontal_separation() {
            var pair = AircraftPairKey.of("BA123", "AF456");
            var posA1 = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 100L, T1);
            var posB1 = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, 100L, T1);
            var posA2 = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 101L, T2);
            var posB2 = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, 101L, T2);
            // First cycle H=3.5, second cycle H=1.2 (closer) → minimum at trajectory index 1
            var event = MinSeparationInfringementEvent.createNew(pair, 100L, T1, 1, List.of(), List.of(), posA1, posB1, 3.5, 900.0);
            event.extendActive(T2, posA2, posB2, 1.2, 300.0);
            event.enterGracePeriod();
            event.decrementGracePeriod();
            event.enterObservationWindow();
            event.close();

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getMinSeparationCycleIndex()).isEqualTo(1);
        }

        // SAFM-76: eventStartCycle and eventEndCycle persistence

        @Test
        void should_persist_event_start_cycle_as_zero_when_no_pre_event_context() {
            var event = closedEvent("BA123", "AF456", 100L);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getEventStartCycle()).isEqualTo(0);
        }

        @Test
        void should_persist_event_start_cycle_equal_to_pre_event_count_when_pre_event_context_present() {
            var event = closedEventWithPreEventContext("BA123", "AF456", 100L, 12);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getEventStartCycle()).isEqualTo(12);
        }

        @Test
        void should_persist_event_end_cycle_matching_last_infringing_cycle_index() {
            // 12 pre-event + 1 infringing → eventEndCycle = 12
            var event = closedEventWithPreEventContext("BA123", "AF456", 100L, 12);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getEventEndCycle()).isEqualTo(12);
        }

        @Test
        void should_persist_event_end_cycle_correctly_for_multi_cycle_infringement() {
            // 12 pre-event + 3 active → eventEndCycle = 14
            var pair = AircraftPairKey.of("BA123", "AF456");
            var prePositions1 = buildPreEventPositions(pair.callsign1(), 12);
            var prePositions2 = buildPreEventPositions(pair.callsign2(), 12);
            var posA = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 100L, T1);
            var posB = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, 100L, T1);
            var event = MinSeparationInfringementEvent.createNew(pair, 100L, T1, 1, prePositions1, prePositions2, posA, posB, 3.5, 800.0);
            event.extendActive(T2,
                    TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 101L, T2),
                    TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, 101L, T2),
                    3.5, 800.0);
            event.extendActive(T2,
                    TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 102L, T2),
                    TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, 102L, T2),
                    3.5, 800.0);
            event.enterGracePeriod();
            event.decrementGracePeriod();
            event.enterObservationWindow();
            event.close();

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getEventEndCycle()).isEqualTo(14);
        }

        @Test
        void should_persist_event_start_cycle_as_partial_pre_event_count_on_cold_start() {
            // Only 5 pre-event positions available (cold start)
            var event = closedEventWithPreEventContext("BA123", "AF456", 100L, 5);

            eventStore.save(event);

            var doc = repository.findById(event.getEventId()).orElseThrow();
            assertThat(doc.getEventStartCycle()).isEqualTo(5);
        }
    }

    // =========================================================================
    // Test object factories
    // =========================================================================

    private static MinSeparationInfringementEvent closedEvent(String c1, String c2, long startCycle) {
        var pair = AircraftPairKey.of(c1, c2);
        var posA = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, startCycle, T1);
        var posB = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, startCycle, T1);
        var event = MinSeparationInfringementEvent.createNew(pair, startCycle, T1, 1, List.of(), List.of(), posA, posB, 3.5, 800.0);
        event.enterGracePeriod();
        event.decrementGracePeriod(); // counter = 0 (gracePeriodCycles=1, so one decrement exhausts)
        event.enterObservationWindow();
        event.close();
        return event;
    }

    private static MinSeparationInfringementEvent closedEventWithMinSep(
            String c1, String c2, long startCycle, double hSepNm, double vSepFt) {
        var pair = AircraftPairKey.of(c1, c2);
        var posA = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, startCycle, T1);
        var posB = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, startCycle, T1);
        var event = MinSeparationInfringementEvent.createNew(pair, startCycle, T1, 1, List.of(), List.of(), posA, posB, hSepNm, vSepFt);
        event.enterGracePeriod();
        event.decrementGracePeriod();
        event.enterObservationWindow();
        event.close();
        return event;
    }

    private static MinSeparationInfringementEvent closedEventWithPreEventContext(
            String c1, String c2, long startCycle, int preEventCount) {
        var pair = AircraftPairKey.of(c1, c2);
        var pre1 = buildPreEventPositions(pair.callsign1(), preEventCount);
        var pre2 = buildPreEventPositions(pair.callsign2(), preEventCount);
        var posA = TrajectoryPosition.from(pair.callsign1(), 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, startCycle, T1);
        var posB = TrajectoryPosition.from(pair.callsign2(), 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, startCycle, T1);
        var event = MinSeparationInfringementEvent.createNew(pair, startCycle, T1, 1, pre1, pre2, posA, posB, 3.5, 800.0);
        event.enterGracePeriod();
        event.decrementGracePeriod();
        event.enterObservationWindow();
        event.close();
        return event;
    }

    private static java.util.List<TrajectoryPosition> buildPreEventPositions(String callsign, int count) {
        var list = new java.util.ArrayList<TrajectoryPosition>(count);
        for (int i = 0; i < count; i++) {
            list.add(TrajectoryPosition.from(callsign, (float) (500 + i), 0.0f, 20000.0f, 0.0f, 0, null, null, 100L - count + i, T1));
        }
        return list;
    }
}
