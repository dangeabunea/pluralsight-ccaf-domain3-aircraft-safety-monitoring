package com.atcsafety.detection.application;

import com.atcsafety.detection.domain.AircraftPairKey;
import com.atcsafety.detection.domain.InfringementDetector;
import com.atcsafety.detection.domain.MinSeparationEventLifecycleManager;
import com.atcsafety.detection.domain.TrajectoryPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CycleDetectionProcessor}.
 *
 * <p>Verifies that the processor correctly translates {@link RadarPositionMessage} DTOs
 * to domain types, handles callsign fallback, updates the {@link AircraftPositionHistory}
 * buffer, snapshots pre-event context before updating the buffer, and wires the domain
 * pipeline in the correct order.
 *
 * <p>No Spring context — pure Mockito + AssertJ.
 */
@ExtendWith(MockitoExtension.class)
class CycleDetectionProcessorTest {

    private static final int MAX_HISTORY = 12;

    @Mock
    private InfringementDetector detector;

    @Mock
    private MinSeparationEventLifecycleManager lifecycleManager;

    private AircraftPositionHistory positionHistory;
    private CycleDetectionProcessor cycleProcessor;

    @BeforeEach
    void setUp() {
        positionHistory = new AircraftPositionHistory(MAX_HISTORY);
        cycleProcessor = new CycleDetectionProcessor(detector, lifecycleManager, positionHistory);
    }

    // =========================================================================
    // Test factory — single place for RadarPositionMessage construction
    // =========================================================================

    private RadarPositionMessage position(int targetId, String callsign) {
        return new RadarPositionMessage(targetId, 5, callsign,
                1000f, 2000f, 20000f, 400f, 0, null, null, "2026-03-01T10:00:00Z");
    }

    @Nested
    class ProcessCompletedCycle {

        @Test
        void should_pass_one_trajectory_position_per_message_to_detector() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            cycleProcessor.processCompletedCycle(
                    List.of(position(1, "BA123"), position(2, "AF456")), 5);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(detector).detectInfringements(captor.capture(), anyLong());
            assertThat(captor.getValue()).hasSize(2);
        }

        @Test
        void should_use_flight_nb_as_callsign_in_trajectory_position() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 5);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(detector).detectInfringements(captor.capture(), anyLong());
            TrajectoryPosition captured = (TrajectoryPosition) captor.getValue().get(0);
            assertThat(captured.callsign()).isEqualTo("BA123");
        }

        @Test
        void should_fall_back_to_target_id_string_when_flight_nb_is_null() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());
            var msgNoCallsign = new RadarPositionMessage(42, 5, null,
                    1000f, 2000f, 20000f, 400f, 0, null, null, "2026-03-01T10:00:00Z");

            cycleProcessor.processCompletedCycle(List.of(msgNoCallsign), 5);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(detector).detectInfringements(captor.capture(), anyLong());
            TrajectoryPosition captured = (TrajectoryPosition) captor.getValue().get(0);
            assertThat(captured.callsign()).isEqualTo("TARGET-42");
        }

        @Test
        void should_pass_detected_infringements_to_lifecycle_manager() {
            var pairKey = AircraftPairKey.of("AF456", "BA123");
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of(pairKey));

            cycleProcessor.processCompletedCycle(
                    List.of(position(1, "BA123"), position(2, "AF456")), 5);

            var captor = ArgumentCaptor.forClass(Set.class);
            verify(lifecycleManager).processInfringingPairs(captor.capture(), anyLong(), any(), any(), any());
            assertThat(captor.getValue()).containsExactly(pairKey);
        }

        @Test
        void should_include_all_callsigns_in_positions_by_callsign_map() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            cycleProcessor.processCompletedCycle(
                    List.of(position(1, "BA123"), position(2, "AF456")), 5);

            var posCaptor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleManager).processInfringingPairs(any(), anyLong(), any(), posCaptor.capture(), any());
            assertThat(posCaptor.getValue()).containsKeys("BA123", "AF456");
        }

        @Test
        void should_not_call_detector_when_cycle_buffer_is_empty() {
            cycleProcessor.processCompletedCycle(List.of(), 5);

            verify(detector, never()).detectInfringements(any(), anyLong());
            verify(lifecycleManager, never()).processInfringingPairs(any(), anyLong(), any(), any(), any());
        }

        @Test
        void should_parse_cycle_timestamp_from_first_message_timestamp_utc() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());
            var msg = new RadarPositionMessage(1, 5, "BA123",
                    1000f, 2000f, 20000f, 400f, 0, null, null, "2026-03-01T10:05:00Z");

            cycleProcessor.processCompletedCycle(List.of(msg), 5);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(detector).detectInfringements(captor.capture(), anyLong());
            TrajectoryPosition captured = (TrajectoryPosition) captor.getValue().get(0);
            assertThat(captured.cycleTimestamp().toString()).isEqualTo("2026-03-01T10:05:00Z");
        }

        @Test
        void should_use_current_time_fallback_when_timestamp_utc_is_unparseable() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());
            var msgBadTimestamp = new RadarPositionMessage(1, 5, "BA123",
                    1000f, 2000f, 20000f, 400f, 0, null, null, "not-a-valid-timestamp");

            // Should not throw — should silently fall back to current time
            cycleProcessor.processCompletedCycle(List.of(msgBadTimestamp), 5);

            verify(detector).detectInfringements(any(), anyLong());
        }

        @Test
        void should_propagate_heading_from_message_to_trajectory_position() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());
            var msg = new RadarPositionMessage(1, 5, "BA123",
                    1000f, 2000f, 20000f, 400f, 270, null, null, "2026-03-01T10:00:00Z");

            cycleProcessor.processCompletedCycle(List.of(msg), 5);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(detector).detectInfringements(captor.capture(), anyLong());
            TrajectoryPosition captured = (TrajectoryPosition) captor.getValue().get(0);
            assertThat(captured.headingDeg()).isEqualTo(270);
        }

        @Test
        void should_propagate_lat_lon_from_message_to_trajectory_position_when_present() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());
            var msg = new RadarPositionMessage(1, 5, "BA123",
                    1000f, 2000f, 20000f, 400f, 135, 51.5f, -0.12f, "2026-03-01T10:00:00Z");

            cycleProcessor.processCompletedCycle(List.of(msg), 5);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(detector).detectInfringements(captor.capture(), anyLong());
            TrajectoryPosition captured = (TrajectoryPosition) captor.getValue().get(0);
            assertThat(captured.lat()).isEqualTo((double) 51.5f);
            assertThat(captured.lon()).isEqualTo((double) -0.12f);
        }

        @Test
        void should_store_null_lat_lon_in_trajectory_position_when_absent_from_message() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());
            var msg = new RadarPositionMessage(1, 5, "BA123",
                    1000f, 2000f, 20000f, 400f, 0, null, null, "2026-03-01T10:00:00Z");

            cycleProcessor.processCompletedCycle(List.of(msg), 5);

            var captor = ArgumentCaptor.forClass(List.class);
            verify(detector).detectInfringements(captor.capture(), anyLong());
            TrajectoryPosition captured = (TrajectoryPosition) captor.getValue().get(0);
            assertThat(captured.lat()).isNull();
            assertThat(captured.lon()).isNull();
        }
    }

    // =========================================================================
    // SAFM-76: AircraftPositionHistory buffer management
    // =========================================================================

    @Nested
    class PositionHistoryBufferManagement {

        @Test
        void should_update_position_history_for_all_aircraft_in_cycle() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            cycleProcessor.processCompletedCycle(
                    List.of(position(1, "BA123"), position(2, "AF456")), 5);

            // Both callsigns should now have an entry in the history
            assertThat(positionHistory.getSnapshot("BA123")).hasSize(1);
            assertThat(positionHistory.getSnapshot("AF456")).hasSize(1);
        }

        @Test
        void should_accumulate_position_history_across_multiple_cycles() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 5);
            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 6);
            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 7);

            assertThat(positionHistory.getSnapshot("BA123")).hasSize(3);
        }

        @Test
        void should_not_update_history_when_cycle_buffer_is_empty() {
            cycleProcessor.processCompletedCycle(List.of(), 5);

            // No history should have been recorded
            assertThat(positionHistory.getSnapshot("BA123")).isEmpty();
        }

        @Test
        void should_pass_pre_event_context_snapshot_to_lifecycle_manager() {
            // Seed history with one position for BA123 before the "infringing" cycle
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());
            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 4);

            // Now process cycle 5 — history should have cycle-4 position as pre-event context
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());
            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 5);

            // The pre-event context map passed to lifecycleManager in the second call
            // should contain BA123 with one entry (the cycle-4 position, captured before
            // the current cycle-5 position was added to the history).
            var preContextCaptor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleManager, org.mockito.Mockito.times(2))
                    .processInfringingPairs(any(), anyLong(), any(), any(), preContextCaptor.capture());

            // The second invocation (cycle 5) captured after BA123 was seeded in cycle 4
            var secondCallContext = (Map<String, List<TrajectoryPosition>>) preContextCaptor.getAllValues().get(1);
            assertThat(secondCallContext).containsKey("BA123");
            assertThat(secondCallContext.get("BA123")).hasSize(1);
        }

        @Test
        void should_snapshot_pre_event_context_before_updating_history_for_current_cycle() {
            // This test verifies that the current cycle's position is NOT included in the
            // pre-event snapshot passed to the lifecycle manager. The snapshot captures
            // only the N cycles PRIOR to the current infringing cycle.
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            // Process 3 cycles to build up history
            for (int i = 1; i <= 3; i++) {
                cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), i);
            }

            // On the 4th cycle, the pre-event context for BA123 should contain exactly 3 entries
            // (cycles 1, 2, 3) — NOT 4 entries (the current cycle 4 position must not be
            // included in the pre-event snapshot).
            var preContextCaptor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleManager, org.mockito.Mockito.times(3))
                    .processInfringingPairs(any(), anyLong(), any(), any(), preContextCaptor.capture());

            // Third call (cycle 3): pre-event context should contain 2 entries (cycles 1 and 2),
            // because cycle 3's position is snapshotted before being added to history.
            var thirdCallContext = (Map<String, List<TrajectoryPosition>>) preContextCaptor.getAllValues().get(2);
            assertThat(thirdCallContext.get("BA123")).hasSize(2);
        }

        @Test
        void should_pass_empty_pre_event_context_for_new_callsign_first_seen_in_cycle() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            cycleProcessor.processCompletedCycle(List.of(position(1, "NEW_AIRCRAFT")), 5);

            var preContextCaptor = ArgumentCaptor.forClass(Map.class);
            verify(lifecycleManager).processInfringingPairs(any(), anyLong(), any(), any(), preContextCaptor.capture());

            var context = (Map<String, List<TrajectoryPosition>>) preContextCaptor.getValue();
            // NEW_AIRCRAFT has never been seen — its pre-event context should be empty
            assertThat(context.get("NEW_AIRCRAFT")).isEmpty();
        }
    }

    // =========================================================================
    // SAFM-80: Stale track eviction
    // =========================================================================

    @Nested
    class StaleTrackEviction {

        @Test
        void should_evict_callsign_from_history_after_3_cycles_with_no_update() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            // Seed BA123 in cycle 1
            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 1);
            assertThat(positionHistory.getSnapshot("BA123")).hasSize(1);

            // Cycles 2, 3, 4 — BA123 absent; counter increments to 3 on cycle 4
            cycleProcessor.processCompletedCycle(List.of(position(2, "AF456")), 2);
            cycleProcessor.processCompletedCycle(List.of(position(2, "AF456")), 3);
            cycleProcessor.processCompletedCycle(List.of(position(2, "AF456")), 4);

            assertThat(positionHistory.getSnapshot("BA123")).isEmpty();
        }

        @Test
        void should_retain_callsign_when_it_reappears_before_eviction_threshold() {
            when(detector.detectInfringements(any(), anyLong())).thenReturn(Set.of());

            // Seed BA123 in cycle 1
            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 1);

            // Two missed cycles — counter reaches 2 (not yet at threshold)
            cycleProcessor.processCompletedCycle(List.of(position(2, "AF456")), 2);
            cycleProcessor.processCompletedCycle(List.of(position(2, "AF456")), 3);

            // BA123 reappears in cycle 4 — counter resets
            cycleProcessor.processCompletedCycle(List.of(position(1, "BA123")), 4);

            // Two more missed cycles — counter is 2 again, still below threshold
            cycleProcessor.processCompletedCycle(List.of(position(2, "AF456")), 5);
            cycleProcessor.processCompletedCycle(List.of(position(2, "AF456")), 6);

            assertThat(positionHistory.getSnapshot("BA123")).isNotEmpty();
        }
    }
}
