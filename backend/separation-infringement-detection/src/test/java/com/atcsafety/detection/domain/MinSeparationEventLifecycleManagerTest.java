package com.atcsafety.detection.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MinSeparationEventLifecycleManager}.
 *
 * <p>Covers all 11 acceptance criteria from SAFM-37, minimum-separation
 * computation from SAFM-46, and the new OBSERVATION_WINDOW lifecycle from SAFM-76.
 * Uses Mockito for the registry and store dependencies;
 * {@link SeparationCalculator} is a real domain object with no framework deps.
 *
 * <p>The lifecycle manager processes pairs in two passes per call:
 * <ol>
 *   <li>Infringing pairs — looked up via {@code registry.contains()} + {@code registry.get()}</li>
 *   <li>Registered-but-non-infringing pairs — iterated via {@code registry.activeKeys()}</li>
 * </ol>
 * Tests that exercise re-separation or grace-period decrement must stub {@code activeKeys()}.
 *
 * <h2>Test object factory</h2>
 * <p>All aggregate construction goes through {@link #existingActive(AircraftPairKey, long)} and
 * its variants. If {@link MinSeparationInfringementEvent#createNew} changes its signature,
 * only those helpers need updating.
 */
@ExtendWith(MockitoExtension.class)
class MinSeparationEventLifecycleManagerTest {

    private static final int GRACE_PERIOD_CYCLES = 3;
    private static final int OBSERVATION_WINDOW_CYCLES = 12;

    private static final Instant T1 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T10:00:10Z");
    private static final Instant T3 = Instant.parse("2026-03-01T10:00:20Z");

    private static final AircraftPairKey PAIR_AB = AircraftPairKey.of("BA123", "AF456");
    private static final AircraftPairKey PAIR_CD = AircraftPairKey.of("KL007", "LH400");

    @Mock
    private MinSeparationActiveEventRegistry registry;

    @Mock
    private MinSeparationEventStore store;

    private MinSeparationEventLifecycleManager lifecycleManager;

    @BeforeEach
    void setUp() {
        lifecycleManager = new MinSeparationEventLifecycleManager(
                registry, store, GRACE_PERIOD_CYCLES, OBSERVATION_WINDOW_CYCLES, new SeparationCalculator());
    }

    // =========================================================================
    // Test object factories
    // =========================================================================

    private TrajectoryPosition pos(String callsign, long cycle, Instant ts) {
        return TrajectoryPosition.from(callsign, 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, cycle, ts);
    }

    private Map<String, TrajectoryPosition> posMap(long cycle, Instant ts, String... callsigns) {
        var map = new HashMap<String, TrajectoryPosition>();
        for (String cs : callsigns) {
            map.put(cs, pos(cs, cycle, ts));
        }
        return map;
    }

    /**
     * Returns a position map where AF456 and BA123 are 1852 m apart horizontally,
     * giving exactly 1.0 NM horizontal separation and 0 ft vertical separation.
     * Used in SAFM-46 separation-computation tests.
     */
    private Map<String, TrajectoryPosition> separatedPosMap(long cycle, Instant ts) {
        var map = new HashMap<String, TrajectoryPosition>();
        map.put("AF456", TrajectoryPosition.from("AF456", 0.0f, 0.0f, 20000.0f, 0.0f, 0, null, null, cycle, ts));
        map.put("BA123", TrajectoryPosition.from("BA123", 1852.0f, 0.0f, 20000.0f, 0.0f, 0, null, null, cycle, ts));
        return map;
    }

    /**
     * Factory for an existing ACTIVE event with no pre-event context.
     * All construction uses the new createNew signature — single place to update.
     */
    private MinSeparationInfringementEvent existingActive(AircraftPairKey pair, long startCycle) {
        return MinSeparationInfringementEvent.createNew(
                pair, startCycle, T1, GRACE_PERIOD_CYCLES,
                List.of(), List.of(),
                pos(pair.callsign1(), startCycle, T1),
                pos(pair.callsign2(), startCycle, T1),
                0.0, 0.0);
    }

    /**
     * Factory for an existing event that is already in OBSERVATION_WINDOW state.
     * Simulates the full lifecycle: create → extend (N active cycles) → grace period
     * → grace exhaustion → enterObservationWindow.
     */
    private MinSeparationInfringementEvent existingObservationWindow(AircraftPairKey pair, long startCycle) {
        var event = existingActive(pair, startCycle);
        event.enterGracePeriod();
        event.decrementGracePeriod(); // counter = 2
        event.decrementGracePeriod(); // counter = 1
        event.decrementGracePeriod(); // counter = 0 → exhausted
        event.enterObservationWindow();
        return event;
    }

    // -----------------------------------------------------------------------
    // AC-1: Active event — pair still infringing — extend event
    // -----------------------------------------------------------------------

    @Nested
    class AC1_ExtendActiveEvent {

        @Test
        void should_append_positions_and_keep_status_active_when_pair_still_infringing() {
            var event = existingActive(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            var positions = posMap(101L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 101L, T2, positions, Map.of());

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
            assertThat(event.getTrajectory1()).hasSize(2);
            assertThat(event.getTrajectory2()).hasSize(2);
        }

        @Test
        void should_not_create_duplicate_event_when_pair_already_active() {
            var event = existingActive(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            var positions = posMap(101L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 101L, T2, positions, Map.of());

            // register called 0 times for this pair (was already registered)
            verify(registry, never()).register(any(), any());
        }
    }

    // -----------------------------------------------------------------------
    // AC-2: Active event — pair re-separates — enter grace period
    // -----------------------------------------------------------------------

    @Nested
    class AC2_EnterGracePeriod {

        @Test
        void should_transition_to_grace_period_when_pair_re_separates() {
            var event = existingActive(PAIR_AB, 100L);
            // Re-separation: pair is NOT in infringingPairs; accessed via activeKeys() + get()
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            var positions = posMap(101L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(), 101L, T2, positions, Map.of());

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.GRACE_PERIOD);
            assertThat(event.getGracePeriodCounter()).isEqualTo(GRACE_PERIOD_CYCLES);
        }

        @Test
        void should_not_close_event_on_first_re_separation_cycle() {
            var event = existingActive(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            var positions = posMap(101L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(), 101L, T2, positions, Map.of());

            verify(store, never()).save(any());
            verify(registry, never()).remove(any());
        }
    }

    // -----------------------------------------------------------------------
    // AC-3: Grace period — pair re-enters infringement — reset to active
    // -----------------------------------------------------------------------

    @Nested
    class AC3_ReenterFromGracePeriod {

        @Test
        void should_reset_to_active_when_pair_re_enters_infringement_during_grace() {
            var event = existingActive(PAIR_AB, 100L);
            event.enterGracePeriod();
            event.decrementGracePeriod(); // counter = 2
            // Re-entry: pair IS in infringingPairs; accessed via contains() + get()
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            var positions = posMap(102L, T3, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 102L, T3, positions, Map.of());

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
            assertThat(event.getGracePeriodCounter()).isEqualTo(GRACE_PERIOD_CYCLES);
        }

        @Test
        void should_append_positions_on_re_entry_to_active() {
            var event = existingActive(PAIR_AB, 100L);
            event.enterGracePeriod();
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            var positions = posMap(101L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 101L, T2, positions, Map.of());

            assertThat(event.getTrajectory1()).hasSize(2);
        }
    }

    // -----------------------------------------------------------------------
    // AC-4: Grace period exhausted — enter observation window (SAFM-76)
    // -----------------------------------------------------------------------

    @Nested
    class AC4_ObservationWindowAfterGracePeriodExhaustion {

        @Test
        void should_enter_observation_window_when_grace_period_exhausted() {
            var event = existingActive(PAIR_AB, 100L);
            event.enterGracePeriod();
            event.decrementGracePeriod(); // counter = 2
            event.decrementGracePeriod(); // counter = 1
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            var positions = posMap(103L, T3, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(), 103L, T3, positions, Map.of());

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.OBSERVATION_WINDOW);
        }

        @Test
        void should_not_persist_event_immediately_when_grace_period_exhausted() {
            var event = existingActive(PAIR_AB, 100L);
            event.enterGracePeriod();
            event.decrementGracePeriod(); // counter = 2
            event.decrementGracePeriod(); // counter = 1
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            lifecycleManager.processInfringingPairs(
                    Set.of(), 103L, T3, posMap(103L, T3, "AF456", "BA123"), Map.of());

            // Store must NOT be called yet — event is in observation window
            verify(store, never()).save(any());
            verify(registry, never()).remove(any());
        }

        @Test
        void should_persist_event_with_closed_status_not_observation_window_when_grace_period_exhausted_legacy_behavior_replaced() {
            // Regression guard: the old behavior (grace period → close immediately) is now
            // replaced by observation window. This test confirms grace exhaustion → OBSERVATION_WINDOW,
            // not → CLOSED directly.
            var event = existingActive(PAIR_AB, 100L);
            event.enterGracePeriod();
            event.decrementGracePeriod(); // counter = 2
            event.decrementGracePeriod(); // counter = 1
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            lifecycleManager.processInfringingPairs(
                    Set.of(), 103L, T3, posMap(103L, T3, "AF456", "BA123"), Map.of());

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.OBSERVATION_WINDOW);
        }
    }

    // -----------------------------------------------------------------------
    // SAFM-76: OBSERVATION_WINDOW — appending positions and completing window
    // -----------------------------------------------------------------------

    @Nested
    class ObservationWindow {

        @Test
        void should_append_post_event_positions_during_observation_window() {
            var event = existingObservationWindow(PAIR_AB, 100L);
            int sizeBeforeObs = event.getTrajectory1().size();
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            lifecycleManager.processInfringingPairs(
                    Set.of(), 105L, T2, posMap(105L, T2, "AF456", "BA123"), Map.of());

            assertThat(event.getTrajectory1()).hasSize(sizeBeforeObs + 1);
            assertThat(event.getTrajectory2()).hasSize(sizeBeforeObs + 1);
        }

        @Test
        void should_persist_and_remove_event_after_observation_window_completes() {
            var event = existingObservationWindow(PAIR_AB, 100L);
            // Append OBSERVATION_WINDOW_CYCLES - 1 positions first
            for (int i = 0; i < OBSERVATION_WINDOW_CYCLES - 1; i++) {
                event.appendPostEventPosition(
                        pos(PAIR_AB.callsign1(), 105L + i, T2),
                        pos(PAIR_AB.callsign2(), 105L + i, T2));
            }
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            // This call provides the final observation position
            lifecycleManager.processInfringingPairs(
                    Set.of(), 120L, T3, posMap(120L, T3, "AF456", "BA123"), Map.of());

            verify(store).save(event);
            verify(registry).remove(PAIR_AB);
        }

        @Test
        void should_persist_with_closed_status_when_observation_window_completes() {
            var event = existingObservationWindow(PAIR_AB, 100L);
            for (int i = 0; i < OBSERVATION_WINDOW_CYCLES - 1; i++) {
                event.appendPostEventPosition(
                        pos(PAIR_AB.callsign1(), 105L + i, T2),
                        pos(PAIR_AB.callsign2(), 105L + i, T2));
            }
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            MinSeparationEventStatus[] statusAtSaveTime = new MinSeparationEventStatus[1];
            org.mockito.Mockito.doAnswer(inv -> {
                statusAtSaveTime[0] = ((MinSeparationInfringementEvent) inv.getArgument(0)).getStatus();
                return null;
            }).when(store).save(any());

            lifecycleManager.processInfringingPairs(
                    Set.of(), 120L, T3, posMap(120L, T3, "AF456", "BA123"), Map.of());

            assertThat(statusAtSaveTime[0]).isEqualTo(MinSeparationEventStatus.CLOSED);
        }

        @Test
        void should_not_append_positions_during_observation_window_when_aircraft_positions_absent_track_loss() {
            var event = existingObservationWindow(PAIR_AB, 100L);
            int sizeBeforeObs = event.getTrajectory1().size();
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            // No AF456 or BA123 in positions map — track loss
            lifecycleManager.processInfringingPairs(
                    Set.of(), 105L, T2, Map.of(), Map.of());

            // Trajectory should not grow on track loss during observation window
            assertThat(event.getTrajectory1()).hasSize(sizeBeforeObs);
            assertThat(event.getObservationPositionCount()).isEqualTo(0);
        }

        @Test
        void should_not_persist_during_observation_window_before_window_completes() {
            var event = existingObservationWindow(PAIR_AB, 100L);
            // Append OBSERVATION_WINDOW_CYCLES - 2 positions (not enough)
            for (int i = 0; i < OBSERVATION_WINDOW_CYCLES - 2; i++) {
                event.appendPostEventPosition(
                        pos(PAIR_AB.callsign1(), 105L + i, T2),
                        pos(PAIR_AB.callsign2(), 105L + i, T2));
            }
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            lifecycleManager.processInfringingPairs(
                    Set.of(), 118L, T3, posMap(118L, T3, "AF456", "BA123"), Map.of());

            verify(store, never()).save(any());
            verify(registry, never()).remove(any());
        }
    }

    // -----------------------------------------------------------------------
    // SAFM-76: OBSERVATION_WINDOW re-infringement — discard and create fresh event
    // -----------------------------------------------------------------------

    @Nested
    class ObservationWindowReInfringement {

        @Test
        void should_discard_observation_window_event_when_pair_re_infringes() {
            var event = existingObservationWindow(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            var positions = posMap(105L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 105L, T2, positions, Map.of());

            // Old event must be removed from registry
            verify(registry).remove(PAIR_AB);
        }

        @Test
        void should_not_persist_observation_window_event_when_pair_re_infringes() {
            var event = existingObservationWindow(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            var positions = posMap(105L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 105L, T2, positions, Map.of());

            // Discarded event must NOT be persisted
            verify(store, never()).save(event);
        }

        @Test
        void should_create_new_event_after_discarding_observation_window_event_on_re_infringement() {
            var event = existingObservationWindow(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            var positions = posMap(105L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 105L, T2, positions, Map.of());

            // A new event must be registered for the same pair
            var captor = ArgumentCaptor.forClass(MinSeparationInfringementEvent.class);
            verify(registry).register(any(AircraftPairKey.class), captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
            assertThat(captor.getValue().getStartCycle()).isEqualTo(105L);
        }
    }

    // -----------------------------------------------------------------------
    // AC-5: Multiple independent events
    // -----------------------------------------------------------------------

    @Nested
    class AC5_MultipleIndependentEvents {

        @Test
        void should_evaluate_each_pair_independently_when_multiple_pairs_active() {
            var eventAB = existingActive(PAIR_AB, 100L);
            var eventCD = existingActive(PAIR_CD, 100L);

            // AB still infringing (step 1 path: contains + get); CD re-separating (step 2 path: activeKeys + get)
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(eventAB);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB, PAIR_CD));
            org.mockito.Mockito.when(registry.get(PAIR_CD)).thenReturn(eventCD);

            var positions = posMap(101L, T2, "AF456", "BA123", "KL007", "LH400");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 101L, T2, positions, Map.of());

            assertThat(eventAB.getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
            assertThat(eventCD.getStatus()).isEqualTo(MinSeparationEventStatus.GRACE_PERIOD);
        }
    }

    // -----------------------------------------------------------------------
    // AC-6: endTime = lastActiveCycleTimestamp, not updated during grace
    // -----------------------------------------------------------------------

    @Nested
    class AC6_EndTimeIsLastActiveCycleTimestamp {

        @Test
        void should_update_last_active_cycle_timestamp_only_when_active() {
            var event = existingActive(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            // Cycle 101: still infringing → extend active, update timestamp
            var pos101 = posMap(101L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 101L, T2, pos101, Map.of());

            assertThat(event.getLastActiveCycleTimestamp()).isEqualTo(T2);

            // Cycle 102: re-separates → enter grace period, timestamp must NOT change
            var pos102 = posMap(102L, T3, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(), 102L, T3, pos102, Map.of());

            assertThat(event.getLastActiveCycleTimestamp()).isEqualTo(T2);
        }
    }

    // -----------------------------------------------------------------------
    // AC-7: trajectory contains only ACTIVE cycles, index-aligned
    // -----------------------------------------------------------------------

    @Nested
    class AC7_TrajectoryContainsOnlyActiveCycles {

        @Test
        void should_not_add_trajectory_entry_during_grace_period() {
            var event = existingActive(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            // Cycle 101: extend active (2 trajectory entries)
            var pos101 = posMap(101L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 101L, T2, pos101, Map.of());

            // Cycle 102: re-separate → grace period (no new trajectory)
            var pos102 = posMap(102L, T3, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(), 102L, T3, pos102, Map.of());

            assertThat(event.getTrajectory1()).hasSize(2);
            assertThat(event.getTrajectory2()).hasSize(2);
        }

        @Test
        void should_have_index_aligned_trajectories_after_active_cycles() {
            var event = existingActive(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));

            var pos101 = posMap(101L, T2, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 101L, T2, pos101, Map.of());

            var t1 = event.getTrajectory1();
            var t2 = event.getTrajectory2();
            assertThat(t1).hasSameSizeAs(t2);
            for (int i = 0; i < t1.size(); i++) {
                assertThat(t1.get(i).radarCycle()).isEqualTo(t2.get(i).radarCycle());
            }
        }
    }

    // -----------------------------------------------------------------------
    // AC-8: Track loss (one aircraft absent) — treat as re-separation
    // -----------------------------------------------------------------------

    @Nested
    class AC8_TrackLossTreatedAsReSeparation {

        @Test
        void should_start_grace_period_when_only_one_aircraft_present_in_cycle() {
            var event = existingActive(PAIR_AB, 100L);
            // Track loss: pair not in infringingPairs; accessed via activeKeys() + get()
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            // Only BA123 present — AF456 absent (track loss)
            var positions = posMap(101L, T2, "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(), 101L, T2, positions, Map.of());

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.GRACE_PERIOD);
        }

        @Test
        void should_not_add_trajectory_entry_when_track_loss_occurs() {
            var event = existingActive(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            var positions = posMap(101L, T2, "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(), 101L, T2, positions, Map.of());

            assertThat(event.getTrajectory1()).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // AC-9: Grace period counter semantics
    // -----------------------------------------------------------------------

    @Nested
    class AC9_GracePeriodCounterSemantics {

        @Test
        void should_enter_observation_window_exactly_after_grace_period_cycles_re_separation_cycles() {
            // gracePeriodCycles = 3
            // Cycle N (101): re-separate → counter=3
            // Cycle N+1 (102): still separated → counter=2
            // Cycle N+2 (103): still separated → counter=1
            // Cycle N+3 (104): still separated → counter=0 → enter observation window
            var event = existingActive(PAIR_AB, 100L);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);

            // Cycle 101: re-separate → grace period (counter=3)
            lifecycleManager.processInfringingPairs(
                    Set.of(), 101L, T2, posMap(101L, T2, "AF456", "BA123"), Map.of());
            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.GRACE_PERIOD);
            assertThat(event.getGracePeriodCounter()).isEqualTo(3);

            // Cycle 102: still separated → counter=2
            lifecycleManager.processInfringingPairs(
                    Set.of(), 102L, T3, posMap(102L, T3, "AF456", "BA123"), Map.of());
            assertThat(event.getGracePeriodCounter()).isEqualTo(2);

            // Cycle 103: still separated → counter=1
            lifecycleManager.processInfringingPairs(
                    Set.of(), 103L, T3, posMap(103L, T3, "AF456", "BA123"), Map.of());
            assertThat(event.getGracePeriodCounter()).isEqualTo(1);

            // Cycle 104: still separated → counter=0 → enter observation window
            lifecycleManager.processInfringingPairs(
                    Set.of(), 104L, T3, posMap(104L, T3, "AF456", "BA123"), Map.of());
            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.OBSERVATION_WINDOW);
            // Event not persisted yet — observation window must run first
            verify(store, never()).save(event);
        }
    }

    // -----------------------------------------------------------------------
    // AC-10: Exception isolation — per-pair RuntimeException
    // -----------------------------------------------------------------------

    @Nested
    class AC10_ExceptionIsolation {

        @Test
        void should_continue_processing_other_pairs_when_one_pair_throws_exception() {
            // AB throws exception in step 1 (infringing pairs loop)
            var eventCD = existingActive(PAIR_CD, 100L);

            org.mockito.Mockito.when(registry.contains(PAIR_AB))
                    .thenThrow(new RuntimeException("Simulated failure for AB"));
            org.mockito.Mockito.when(registry.contains(PAIR_CD)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_CD)).thenReturn(eventCD);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_CD));

            var positions = posMap(101L, T2, "AF456", "BA123", "KL007", "LH400");
            // Should not throw — exception must be caught per pair
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB, PAIR_CD), 101L, T2, positions, Map.of());

            // CD processed correctly despite AB failure
            assertThat(eventCD.getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
            assertThat(eventCD.getTrajectory1()).hasSize(2);
        }

        @Test
        void should_continue_processing_other_non_infringing_pairs_when_one_throws_during_step2() {
            // AB throws when get() is called in step-2; CD has grace period counter=1 and must still enter OW
            var eventCD = existingActive(PAIR_CD, 100L);
            eventCD.enterGracePeriod();          // counter=3
            eventCD.decrementGracePeriod();      // counter=2
            eventCD.decrementGracePeriod();      // counter=1
            // processNonInfringingPair will decrement once more → counter=0 → observation window

            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB, PAIR_CD));
            org.mockito.Mockito.when(registry.get(PAIR_AB))
                    .thenThrow(new RuntimeException("Simulated step-2 failure for AB"));
            org.mockito.Mockito.when(registry.get(PAIR_CD)).thenReturn(eventCD);

            lifecycleManager.processInfringingPairs(Set.of(), 103L, T3, Map.of(), Map.of());

            // CD should have entered observation window — not persisted yet
            assertThat(eventCD.getStatus()).isEqualTo(MinSeparationEventStatus.OBSERVATION_WINDOW);
            verify(store, never()).save(eventCD);
        }
    }

    // -----------------------------------------------------------------------
    // AC-11: Configurable grace period
    // -----------------------------------------------------------------------

    @Nested
    class AC11_ConfigurableGracePeriod {

        @Test
        void should_use_configured_grace_period_when_set_to_five() {
            // New lifecycle manager with gracePeriodCycles=5
            var manager5 = new MinSeparationEventLifecycleManager(
                    registry, store, 5, OBSERVATION_WINDOW_CYCLES, new SeparationCalculator());
            var eventWith5 = MinSeparationInfringementEvent.createNew(
                    PAIR_AB, 100L, T1, 5,
                    List.of(), List.of(),
                    TrajectoryPosition.from("AF456", 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 100L, T1),
                    TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 100L, T1),
                    0.0, 0.0);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(eventWith5);

            // Re-separate → grace period should be 5
            var positions = posMap(101L, T2, "AF456", "BA123");
            manager5.processInfringingPairs(
                    Set.of(), 101L, T2, positions, Map.of());

            assertThat(eventWith5.getStatus()).isEqualTo(MinSeparationEventStatus.GRACE_PERIOD);
            assertThat(eventWith5.getGracePeriodCounter()).isEqualTo(5);
        }
    }

    // -----------------------------------------------------------------------
    // New pair — create and register (uses pre-event context map)
    // -----------------------------------------------------------------------

    @Nested
    class NewPairCreation {

        @Test
        void should_register_new_event_when_pair_first_appears() {
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(false);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of());

            var positions = posMap(100L, T1, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 100L, T1, positions, Map.of());

            var captor = ArgumentCaptor.forClass(MinSeparationInfringementEvent.class);
            verify(registry).register(any(AircraftPairKey.class), captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
        }

        @Test
        void should_set_start_cycle_and_start_time_on_new_event() {
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(false);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of());

            var positions = posMap(100L, T1, "AF456", "BA123");
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 100L, T1, positions, Map.of());

            var captor = ArgumentCaptor.forClass(MinSeparationInfringementEvent.class);
            verify(registry).register(any(AircraftPairKey.class), captor.capture());
            assertThat(captor.getValue().getStartCycle()).isEqualTo(100L);
            assertThat(captor.getValue().getStartTime()).isEqualTo(T1);
        }

        @Test
        void should_prepend_pre_event_context_when_available_for_new_pair() {
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(false);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of());

            var positions = posMap(100L, T1, "AF456", "BA123");

            // Pre-event context: 3 positions for each aircraft
            var preA = List.of(
                    pos("AF456", 97L, T1),
                    pos("AF456", 98L, T1),
                    pos("AF456", 99L, T1));
            var preB = List.of(
                    pos("BA123", 97L, T1),
                    pos("BA123", 98L, T1),
                    pos("BA123", 99L, T1));
            var preEventContext = Map.of(
                    "AF456", preA,
                    "BA123", preB);

            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 100L, T1, positions, preEventContext);

            var captor = ArgumentCaptor.forClass(MinSeparationInfringementEvent.class);
            verify(registry).register(any(AircraftPairKey.class), captor.capture());
            // 3 pre-event + 1 infringing = 4
            assertThat(captor.getValue().getTrajectory1()).hasSize(4);
            assertThat(captor.getValue().getEventStartCycle()).isEqualTo(3);
        }
    }

    // -----------------------------------------------------------------------
    // SAFM-46: Separation computation and passing
    // -----------------------------------------------------------------------

    @Nested
    class MinSeparationComputation {

        @Test
        void should_compute_horizontal_separation_from_positions_and_set_it_on_new_event() {
            // AF456 at (0,0,20000), BA123 at (1852,0,20000) → H = 1852/1852 = 1.0 NM, V = 0 ft
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(false);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of());

            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 100L, T1, separatedPosMap(100L, T1), Map.of());

            var captor = ArgumentCaptor.forClass(MinSeparationInfringementEvent.class);
            verify(registry).register(any(AircraftPairKey.class), captor.capture());
            assertThat(captor.getValue().getMinHorizontalSeparationNm()).isEqualTo(1.0);
        }

        @Test
        void should_update_min_separation_when_extending_active_event_with_closer_positions() {
            // First cycle: AF456 at (3704,0,20000), BA123 at (0,0,20000) → H = 2.0 NM
            var startPositions = new HashMap<String, TrajectoryPosition>();
            startPositions.put("AF456", TrajectoryPosition.from("AF456", 3704.0f, 0.0f, 20000.0f, 0.0f, 0, null, null, 100L, T1));
            startPositions.put("BA123", TrajectoryPosition.from("BA123", 0.0f, 0.0f, 20000.0f, 0.0f, 0, null, null, 100L, T1));

            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(false);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of());
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 100L, T1, startPositions, Map.of());

            var captor = ArgumentCaptor.forClass(MinSeparationInfringementEvent.class);
            verify(registry).register(any(AircraftPairKey.class), captor.capture());
            var event = captor.getValue();
            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(2.0);

            // Second cycle: AF456 at (0,0,20000), BA123 at (1852,0,20000) → H = 1.0 NM (closer)
            org.mockito.Mockito.when(registry.contains(PAIR_AB)).thenReturn(true);
            org.mockito.Mockito.when(registry.get(PAIR_AB)).thenReturn(event);
            org.mockito.Mockito.when(registry.activeKeys()).thenReturn(Set.of(PAIR_AB));
            lifecycleManager.processInfringingPairs(
                    Set.of(PAIR_AB), 101L, T2, separatedPosMap(101L, T2), Map.of());

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(1.0);
        }
    }
}
