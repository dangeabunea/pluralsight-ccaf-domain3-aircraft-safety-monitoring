package com.atcsafety.detection.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MinSeparationInfringementEvent}.
 *
 * <p>Covers the static factory, initial state invariants, all lifecycle
 * transition methods (extendActive, enterGracePeriod, decrementGracePeriod,
 * isGracePeriodExhausted, reenterActive), minimum-separation tracking
 * (SAFM-46), and the new pre/post-event buffering fields introduced in
 * SAFM-76 (eventStartCycle, eventEndCycle, OBSERVATION_WINDOW lifecycle,
 * appendPostEventPosition, isObservationWindowComplete).
 *
 * <p>Pure Java — no Spring context.
 *
 * <h2>Test object factory</h2>
 * <p>All aggregate construction goes through the static helpers below.
 * If {@link MinSeparationInfringementEvent#createNew} or
 * {@link MinSeparationInfringementEvent#createNewWithPreEventContext} changes
 * its signature, only this class needs updating.
 */
class MinSeparationInfringementEventTest {

    private static final Instant T1 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T10:00:10Z");
    private static final Instant T3 = Instant.parse("2026-03-01T10:00:20Z");

    // Arbitrary infringing separation values — used in tests that do not exercise min-sep tracking
    private static final double DEFAULT_H_SEP_NM = 3.5;
    private static final double DEFAULT_V_SEP_FT = 800.0;

    private static final AircraftPairKey PAIR = AircraftPairKey.of("BA123", "AF456");

    // =========================================================================
    // Test object factories (single place for aggregate construction)
    // =========================================================================

    /** Creates a position for callsign1 at a given cycle using a fixed timestamp. */
    private static TrajectoryPosition pos1(long cycle) {
        return TrajectoryPosition.from("BA123", 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, cycle, T1);
    }

    /** Creates a position for callsign2 at a given cycle using a fixed timestamp. */
    private static TrajectoryPosition pos2(long cycle) {
        return TrajectoryPosition.from("AF456", 1500.0f, 2500.0f, 20500.0f, 0.0f, 0, null, null, cycle, T1);
    }

    /**
     * Creates a new event with no pre-event context (legacy style).
     * Uses {@link MinSeparationInfringementEvent#createNew} with empty pre-event lists.
     */
    private static MinSeparationInfringementEvent newEvent() {
        return MinSeparationInfringementEvent.createNew(
                PAIR, 100L, T1, 3, List.of(), List.of(),
                pos1(100), pos2(100), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
    }

    /**
     * Creates a new event with pre-event context: N positions per aircraft prepended
     * before the first infringing position.
     */
    private static MinSeparationInfringementEvent newEventWithPreEventContext(int preEventCount) {
        List<TrajectoryPosition> pre1 = new ArrayList<>();
        List<TrajectoryPosition> pre2 = new ArrayList<>();
        for (int i = 0; i < preEventCount; i++) {
            pre1.add(TrajectoryPosition.from("BA123", (float) (100 + i), 2000.0f, 20000.0f, 0.0f,
                    0, null, null, 90L + i, T1));
            pre2.add(TrajectoryPosition.from("AF456", (float) (200 + i), 2500.0f, 20500.0f, 0.0f,
                    0, null, null, 90L + i, T1));
        }
        return MinSeparationInfringementEvent.createNew(
                PAIR, 100L, T1, 3, pre1, pre2,
                pos1(100), pos2(100), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
    }

    /**
     * Creates a new event, extends it by {@code additionalActiveCycles} active cycles,
     * then enters the grace period and exhausts it. Returns the event after grace exhaustion
     * but before calling {@code close()} — ready for {@code enterObservationWindow()}.
     */
    private static MinSeparationInfringementEvent eventReadyForObservation(int additionalActiveCycles) {
        var event = newEventWithPreEventContext(12);
        for (int i = 1; i <= additionalActiveCycles; i++) {
            event.extendActive(T2, pos1(100 + i), pos2(100 + i), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
        }
        event.enterGracePeriod();
        for (int i = 0; i < 3; i++) {
            event.decrementGracePeriod();
        }
        return event;
    }

    // =========================================================================
    // Original tests (createNew — no pre-event context)
    // =========================================================================

    @Nested
    class CreateNew {

        @Test
        void should_set_event_id_from_pair_and_start_cycle() {
            var event = newEvent();

            assertThat(event.getEventId()).isEqualTo("AF456-BA123-100");
        }

        @Test
        void should_set_callsigns_from_pair_key() {
            var event = newEvent();

            assertThat(event.getCallsign1()).isEqualTo(PAIR.callsign1());
            assertThat(event.getCallsign2()).isEqualTo(PAIR.callsign2());
        }

        @Test
        void should_set_status_to_active_on_creation() {
            var event = newEvent();

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
        }

        @Test
        void should_set_start_cycle_and_start_time_on_creation() {
            var event = newEvent();

            assertThat(event.getStartCycle()).isEqualTo(100L);
            assertThat(event.getStartTime()).isEqualTo(T1);
        }

        @Test
        void should_set_last_active_cycle_timestamp_on_creation() {
            var event = newEvent();

            assertThat(event.getLastActiveCycleTimestamp()).isEqualTo(T1);
        }

        @Test
        void should_store_grace_period_cycles_as_final_field() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 5, List.of(), List.of(),
                    pos1(100), pos2(100), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getGracePeriodCycles()).isEqualTo(5);
        }

        @Test
        void should_add_first_positions_to_trajectories_on_creation_when_no_pre_event_context() {
            var p1 = pos1(100);
            var p2 = pos2(100);
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(), p1, p2,
                    DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getTrajectory1()).containsExactly(p1);
            assertThat(event.getTrajectory2()).containsExactly(p2);
        }

        @Test
        void should_throw_when_grace_period_cycles_is_zero() {
            assertThatThrownBy(() -> MinSeparationInfringementEvent.createNew(
                            PAIR, 100L, T1, 0, List.of(), List.of(),
                            pos1(100), pos2(100), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gracePeriodCycles must be positive");
        }

        @Test
        void should_throw_when_grace_period_cycles_is_negative() {
            assertThatThrownBy(() -> MinSeparationInfringementEvent.createNew(
                            PAIR, 100L, T1, -1, List.of(), List.of(),
                            pos1(100), pos2(100), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // SAFM-76: Pre-event context — trajectory prepend and index offsets
    // =========================================================================

    @Nested
    class CreateNewWithPreEventContext {

        @Test
        void should_prepend_pre_event_positions_before_first_infringing_position() {
            var pre1 = TrajectoryPosition.from("BA123", 500.0f, 0.0f, 20000.0f, 0.0f, 0, null, null, 98L, T1);
            var pre2 = TrajectoryPosition.from("AF456", 600.0f, 0.0f, 20500.0f, 0.0f, 0, null, null, 98L, T1);
            var infA = pos1(100);
            var infB = pos2(100);

            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(pre1), List.of(pre2), infA, infB,
                    DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getTrajectory1()).containsExactly(pre1, infA);
            assertThat(event.getTrajectory2()).containsExactly(pre2, infB);
        }

        @Test
        void should_set_event_start_cycle_to_size_of_pre_event_context() {
            var event = newEventWithPreEventContext(12);

            assertThat(event.getEventStartCycle()).isEqualTo(12);
        }

        @Test
        void should_set_event_start_cycle_to_zero_when_no_pre_event_context() {
            var event = newEvent();

            assertThat(event.getEventStartCycle()).isEqualTo(0);
        }

        @Test
        void should_set_event_start_cycle_to_actual_count_on_cold_start_with_fewer_than_12_pre_event_positions() {
            var event = newEventWithPreEventContext(5);

            assertThat(event.getEventStartCycle()).isEqualTo(5);
        }

        @Test
        void should_set_min_separation_cycle_index_offset_by_pre_event_count() {
            var event = newEventWithPreEventContext(12);

            // First infringing position is at index 12 (after 12 pre-event entries)
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(12);
        }

        @Test
        void should_set_min_separation_cycle_index_to_zero_when_no_pre_event_context() {
            var event = newEvent();

            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(0);
        }

        @Test
        void should_set_trajectory_size_to_pre_event_count_plus_one_after_creation() {
            var event = newEventWithPreEventContext(12);

            assertThat(event.getTrajectory1()).hasSize(13);
            assertThat(event.getTrajectory2()).hasSize(13);
        }

        @Test
        void should_set_event_end_cycle_to_sentinel_on_creation() {
            // eventEndCycle is -1 until enterObservationWindow() is called
            var event = newEventWithPreEventContext(12);

            assertThat(event.getEventEndCycle()).isEqualTo(-1);
        }
    }

    // =========================================================================
    // SAFM-76: eventStartCycle / eventEndCycle
    // =========================================================================

    @Nested
    class EventStartCycleAndEndCycle {

        @Test
        void should_have_event_start_cycle_equal_to_pre_event_count_when_fully_buffered() {
            var event = newEventWithPreEventContext(12);

            assertThat(event.getEventStartCycle()).isEqualTo(12);
        }

        @Test
        void should_compute_event_end_cycle_correctly_after_single_infringing_cycle() {
            // 12 pre-event + 1 infringing → trajectory size = 13 before observation window
            // eventEndCycle = trajectory1.size() - 1 = 12 (last index of infringing portion)
            var event = newEventWithPreEventContext(12);
            event.enterGracePeriod();
            for (int i = 0; i < 3; i++) {
                event.decrementGracePeriod();
            }

            event.enterObservationWindow();

            assertThat(event.getEventEndCycle()).isEqualTo(12);
        }

        @Test
        void should_compute_event_end_cycle_correctly_after_multiple_infringing_cycles() {
            // 12 pre-event + 3 active infringement cycles → trajectory size = 15
            // eventEndCycle = 15 - 1 = 14
            var event = newEventWithPreEventContext(12);
            event.extendActive(T2, pos1(101), pos2(101), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.extendActive(T2, pos1(102), pos2(102), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.enterGracePeriod();
            for (int i = 0; i < 3; i++) {
                event.decrementGracePeriod();
            }

            event.enterObservationWindow();

            assertThat(event.getEventEndCycle()).isEqualTo(14);
        }

        @Test
        void should_preserve_event_start_cycle_unchanged_through_extend_and_grace_period() {
            var event = newEventWithPreEventContext(12);
            event.extendActive(T2, pos1(101), pos2(101), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.enterGracePeriod();
            event.decrementGracePeriod();

            assertThat(event.getEventStartCycle()).isEqualTo(12);
        }
    }

    // =========================================================================
    // SAFM-76: enterObservationWindow
    // =========================================================================

    @Nested
    class EnterObservationWindow {

        @Test
        void should_set_status_to_observation_window() {
            var event = eventReadyForObservation(0);

            event.enterObservationWindow();

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.OBSERVATION_WINDOW);
        }

        @Test
        void should_set_event_end_cycle_to_last_index_of_trajectory_when_entering_observation_window() {
            // 12 pre-event + 1 active = trajectory size 13 → last index 12
            var event = eventReadyForObservation(0);

            event.enterObservationWindow();

            assertThat(event.getEventEndCycle()).isEqualTo(12);
        }

        @Test
        void should_initialize_observation_position_count_to_zero() {
            var event = eventReadyForObservation(0);

            event.enterObservationWindow();

            assertThat(event.getObservationPositionCount()).isEqualTo(0);
        }

        @Test
        void should_not_change_trajectory_size_when_entering_observation_window() {
            var event = eventReadyForObservation(0);
            int sizeBeforeObservation = event.getTrajectory1().size();

            event.enterObservationWindow();

            assertThat(event.getTrajectory1()).hasSize(sizeBeforeObservation);
        }

        @Test
        void should_not_change_event_start_cycle_when_entering_observation_window() {
            var event = eventReadyForObservation(0);

            event.enterObservationWindow();

            assertThat(event.getEventStartCycle()).isEqualTo(12);
        }
    }

    // =========================================================================
    // SAFM-76: appendPostEventPosition
    // =========================================================================

    @Nested
    class AppendPostEventPosition {

        @Test
        void should_append_positions_to_both_trajectories() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();
            int sizeBefore = event.getTrajectory1().size();
            var obsA = pos1(200);
            var obsB = pos2(200);

            event.appendPostEventPosition(obsA, obsB);

            assertThat(event.getTrajectory1()).hasSize(sizeBefore + 1);
            assertThat(event.getTrajectory2()).hasSize(sizeBefore + 1);
            assertThat(event.getTrajectory1()).endsWith(obsA);
            assertThat(event.getTrajectory2()).endsWith(obsB);
        }

        @Test
        void should_increment_observation_position_count_on_each_call() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();

            event.appendPostEventPosition(pos1(200), pos2(200));
            event.appendPostEventPosition(pos1(201), pos2(201));

            assertThat(event.getObservationPositionCount()).isEqualTo(2);
        }

        @Test
        void should_not_change_event_end_cycle_after_appending_post_event_positions() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();
            int endCycleAtObservationStart = event.getEventEndCycle();

            event.appendPostEventPosition(pos1(200), pos2(200));
            event.appendPostEventPosition(pos1(201), pos2(201));

            assertThat(event.getEventEndCycle()).isEqualTo(endCycleAtObservationStart);
        }

        @Test
        void should_keep_event_start_cycle_unchanged_after_appending_post_event_positions() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();

            for (int i = 0; i < 5; i++) {
                event.appendPostEventPosition(pos1(200 + i), pos2(200 + i));
            }

            assertThat(event.getEventStartCycle()).isEqualTo(12);
        }

        @Test
        void should_keep_status_as_observation_window_while_appending_post_event_positions() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();

            event.appendPostEventPosition(pos1(200), pos2(200));

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.OBSERVATION_WINDOW);
        }
    }

    // =========================================================================
    // SAFM-76: isObservationWindowComplete
    // =========================================================================

    @Nested
    class IsObservationWindowComplete {

        @Test
        void should_return_false_when_no_post_event_positions_appended() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();

            assertThat(event.isObservationWindowComplete(12)).isFalse();
        }

        @Test
        void should_return_false_when_fewer_than_window_size_post_event_positions_appended() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();

            for (int i = 0; i < 11; i++) {
                event.appendPostEventPosition(pos1(200 + i), pos2(200 + i));
            }

            assertThat(event.isObservationWindowComplete(12)).isFalse();
        }

        @Test
        void should_return_true_exactly_when_observation_position_count_reaches_window_size() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();

            for (int i = 0; i < 12; i++) {
                event.appendPostEventPosition(pos1(200 + i), pos2(200 + i));
            }

            assertThat(event.isObservationWindowComplete(12)).isTrue();
        }

        @Test
        void should_return_true_when_position_count_exceeds_window_size() {
            var event = eventReadyForObservation(0);
            event.enterObservationWindow();

            for (int i = 0; i < 15; i++) {
                event.appendPostEventPosition(pos1(200 + i), pos2(200 + i));
            }

            assertThat(event.isObservationWindowComplete(12)).isTrue();
        }

        @Test
        void should_return_false_before_entering_observation_window() {
            var event = newEventWithPreEventContext(12);

            // isObservationWindowComplete called before observation window — count is 0
            assertThat(event.isObservationWindowComplete(12)).isFalse();
        }
    }

    // =========================================================================
    // SAFM-76: trajectory contains pre-event + active + post-event positions
    // =========================================================================

    @Nested
    class FullLifecycleTrajectorySize {

        @Test
        void should_have_trajectory_size_of_pre_plus_active_plus_post_event_cycles_after_full_lifecycle() {
            // 12 pre + 3 active + 12 post = 27 positions
            var event = newEventWithPreEventContext(12);
            event.extendActive(T2, pos1(101), pos2(101), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.extendActive(T2, pos1(102), pos2(102), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.enterGracePeriod();
            for (int i = 0; i < 3; i++) {
                event.decrementGracePeriod();
            }
            event.enterObservationWindow();
            for (int i = 0; i < 12; i++) {
                event.appendPostEventPosition(pos1(200 + i), pos2(200 + i));
            }

            assertThat(event.getTrajectory1()).hasSize(27);
            assertThat(event.getTrajectory2()).hasSize(27);
        }

        @Test
        void should_have_trajectory_indexed_correctly_for_event_start_and_end_cycle() {
            // 12 pre + 3 active (indices 12,13,14 are infringing) → eventEndCycle = 14
            var event = newEventWithPreEventContext(12);
            event.extendActive(T2, pos1(101), pos2(101), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.extendActive(T2, pos1(102), pos2(102), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.enterGracePeriod();
            for (int i = 0; i < 3; i++) {
                event.decrementGracePeriod();
            }
            event.enterObservationWindow();

            assertThat(event.getEventStartCycle()).isEqualTo(12);
            assertThat(event.getEventEndCycle()).isEqualTo(14);
        }
    }

    // =========================================================================
    // Original tests — extendActive
    // =========================================================================

    @Nested
    class ExtendActive {

        @Test
        void should_append_positions_to_trajectories_when_extending_active_event() {
            var event = newEvent();
            var p1next = pos1(101);
            var p2next = pos2(101);

            event.extendActive(T2, p1next, p2next, DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getTrajectory1()).hasSize(2).endsWith(p1next);
            assertThat(event.getTrajectory2()).hasSize(2).endsWith(p2next);
        }

        @Test
        void should_update_last_active_cycle_timestamp_when_extending() {
            var event = newEvent();

            event.extendActive(T2, pos1(101), pos2(101), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getLastActiveCycleTimestamp()).isEqualTo(T2);
        }

        @Test
        void should_keep_status_as_active_after_extending() {
            var event = newEvent();

            event.extendActive(T2, pos1(101), pos2(101), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
        }

        @Test
        void should_update_min_separation_cycle_index_relative_to_full_trajectory_including_pre_event_entries() {
            // 12 pre-event + 1 active (index 12). Extending adds index 13.
            // If separation at index 13 < separation at index 12, minSeparationCycleIndex = 13.
            var event = newEventWithPreEventContext(12);
            // Creation: hSep = DEFAULT_H_SEP_NM = 3.5 → minSeparationCycleIndex = 12
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(12);

            event.extendActive(T2, pos1(101), pos2(101), 2.0, DEFAULT_V_SEP_FT); // closer

            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(13);
        }

        @Test
        void should_track_vertical_minimum_independently_when_vertical_improves_without_horizontal_improvement() {
            // AC-1: vertical tracker is decoupled from horizontal tracker (SAFM-64)
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);

            event.extendActive(T2, pos1(101), pos2(101), 4.2, 500.0); // horizontal worsens, vertical improves

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(3.5);
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(0);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(500.0);
            assertThat(event.getMinVerticalSeparationCycleIndex()).isEqualTo(1);
        }

        @Test
        void should_not_update_min_vertical_separation_when_vertical_separation_does_not_improve() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 500.0); // low vSep already at creation

            event.extendActive(T2, pos1(101), pos2(101), 2.0, 800.0); // vertical worsens

            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(500.0);
            assertThat(event.getMinVerticalSeparationCycleIndex()).isEqualTo(0);
        }
    }

    // =========================================================================
    // Original tests — enterGracePeriod
    // =========================================================================

    @Nested
    class EnterGracePeriod {

        @Test
        void should_set_status_to_grace_period_when_entering_grace() {
            var event = newEvent();

            event.enterGracePeriod();

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.GRACE_PERIOD);
        }

        @Test
        void should_set_counter_to_grace_period_cycles_when_entering_grace() {
            var event = newEvent();

            event.enterGracePeriod();

            assertThat(event.getGracePeriodCounter()).isEqualTo(3);
        }

        @Test
        void should_not_append_positions_when_entering_grace_period() {
            var event = newEvent();

            event.enterGracePeriod();

            assertThat(event.getTrajectory1()).hasSize(1);
            assertThat(event.getTrajectory2()).hasSize(1);
        }
    }

    // =========================================================================
    // Original tests — decrementGracePeriod
    // =========================================================================

    @Nested
    class DecrementGracePeriod {

        @Test
        void should_decrement_counter_by_one_each_call() {
            var event = newEvent();
            event.enterGracePeriod();

            event.decrementGracePeriod();

            assertThat(event.getGracePeriodCounter()).isEqualTo(2);
        }

        @Test
        void should_reach_zero_after_grace_period_cycles_decrements() {
            var event = newEvent();
            event.enterGracePeriod();

            event.decrementGracePeriod();
            event.decrementGracePeriod();
            event.decrementGracePeriod();

            assertThat(event.getGracePeriodCounter()).isEqualTo(0);
        }

        @Test
        void should_not_go_below_zero_when_called_more_times_than_grace_period_cycles() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 1, List.of(), List.of(),
                    pos1(100), pos2(100), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.enterGracePeriod(); // counter = 1
            event.decrementGracePeriod(); // counter = 0
            event.decrementGracePeriod(); // must stay at 0

            assertThat(event.getGracePeriodCounter()).isEqualTo(0);
        }
    }

    // =========================================================================
    // Original tests — isGracePeriodExhausted
    // =========================================================================

    @Nested
    class IsGracePeriodExhausted {

        @Test
        void should_return_false_when_counter_is_above_zero() {
            var event = newEvent();
            event.enterGracePeriod();
            event.decrementGracePeriod();

            assertThat(event.isGracePeriodExhausted()).isFalse();
        }

        @Test
        void should_return_true_when_counter_reaches_zero() {
            var event = newEvent();
            event.enterGracePeriod();
            event.decrementGracePeriod();
            event.decrementGracePeriod();
            event.decrementGracePeriod();

            assertThat(event.isGracePeriodExhausted()).isTrue();
        }
    }

    // =========================================================================
    // Original tests — reenterActive
    // =========================================================================

    @Nested
    class ReenterActive {

        @Test
        void should_set_status_back_to_active_on_reenter() {
            var event = newEvent();
            event.enterGracePeriod();
            event.decrementGracePeriod();

            event.reenterActive(T3, pos1(102), pos2(102), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.ACTIVE);
        }

        @Test
        void should_reset_counter_to_grace_period_cycles_on_reenter() {
            var event = newEvent();
            event.enterGracePeriod();
            event.decrementGracePeriod();

            event.reenterActive(T3, pos1(102), pos2(102), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getGracePeriodCounter()).isEqualTo(3);
        }

        @Test
        void should_append_positions_to_trajectories_on_reenter() {
            var event = newEvent();
            event.enterGracePeriod();
            var p1re = pos1(102);
            var p2re = pos2(102);

            event.reenterActive(T3, p1re, p2re, DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getTrajectory1()).hasSize(2).endsWith(p1re);
        }

        @Test
        void should_update_last_active_cycle_timestamp_on_reenter() {
            var event = newEvent();
            event.enterGracePeriod();

            event.reenterActive(T3, pos1(102), pos2(102), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);

            assertThat(event.getLastActiveCycleTimestamp()).isEqualTo(T3);
        }

        @Test
        void should_not_update_last_active_cycle_timestamp_during_grace_period() {
            // endTime must reflect last ACTIVE cycle only (AC-6)
            var event = newEvent();
            event.extendActive(T2, pos1(101), pos2(101), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.enterGracePeriod();
            event.decrementGracePeriod();

            // after grace period decrement, lastActiveCycleTimestamp must still be T2
            assertThat(event.getLastActiveCycleTimestamp()).isEqualTo(T2);
        }

        @Test
        void should_track_vertical_minimum_independently_on_reenter_when_vertical_improves_without_horizontal_improvement() {
            // AC-1 mirrored for re-entry: vertical tracker is decoupled from horizontal (SAFM-64)
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);
            event.enterGracePeriod();

            event.reenterActive(T2, pos1(101), pos2(101), 4.0, 500.0); // horizontal worsens, vertical improves

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(3.5);
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(0);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(500.0);
            assertThat(event.getMinVerticalSeparationCycleIndex()).isEqualTo(1);
        }
    }

    // =========================================================================
    // Original tests — close
    // =========================================================================

    @Nested
    class Close {

        @Test
        void should_set_status_to_closed_when_called() {
            var event = newEvent();

            event.close();

            assertThat(event.getStatus()).isEqualTo(MinSeparationEventStatus.CLOSED);
        }
    }

    // =========================================================================
    // Original tests — trajectory contains only ACTIVE cycles (no grace-period)
    // =========================================================================

    @Nested
    class TrajectoryContainsOnlyActiveCycles {

        /**
         * AC-7: trajectory arrays must contain only ACTIVE infringement cycles,
         * not grace-period cycles.
         */
        @Test
        void should_not_add_trajectory_entries_during_grace_period() {
            var event = newEvent();
            event.extendActive(T2, pos1(101), pos2(101), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.enterGracePeriod();
            // Simulate 2 grace period decrements (no positions added)
            event.decrementGracePeriod();
            event.decrementGracePeriod();

            // Only 2 ACTIVE cycles in trajectory, not the grace-period cycles
            assertThat(event.getTrajectory1()).hasSize(2);
            assertThat(event.getTrajectory2()).hasSize(2);
        }

        @Test
        void should_only_add_trajectories_on_active_entry_and_reentry() {
            var event = newEvent();
            event.enterGracePeriod();
            event.decrementGracePeriod();
            event.reenterActive(T3, pos1(102), pos2(102), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.extendActive(T2, pos1(103), pos2(103), DEFAULT_H_SEP_NM, DEFAULT_V_SEP_FT);
            event.enterGracePeriod();
            event.decrementGracePeriod();
            event.decrementGracePeriod();
            event.decrementGracePeriod();

            // Three ACTIVE cycles: initial creation, reentry, extend-after-reentry
            assertThat(event.getTrajectory1()).hasSize(3);
        }
    }

    // =========================================================================
    // SAFM-46: minimum horizontal/vertical separation tracking
    // =========================================================================

    /**
     * Tests for SAFM-46: minimum horizontal/vertical separation tracking.
     *
     * <p>The event tracks the running minimum horizontal separation across its
     * entire ACTIVE lifetime (including re-entries). {@code minSeparationCycleIndex}
     * is a 0-based index into the trajectory arrays that points to the cycle where
     * the closest horizontal approach was observed.
     *
     * <p>With SAFM-76 pre-event context, indices are offset by the pre-event count.
     */
    @Nested
    class MinSeparationTracking {

        @Test
        void should_set_min_separation_from_first_positions_on_creation_with_no_pre_event_context() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(3.5);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(900.0);
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(0);
        }

        @Test
        void should_set_min_separation_cycle_index_to_pre_event_count_when_pre_event_context_present() {
            var event = newEventWithPreEventContext(12);

            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(12);
        }

        @Test
        void should_not_update_min_separation_when_new_cycle_has_greater_horizontal_separation() {
            // Horizontal worsens (4.2 > 3.5) → horizontal tracker unchanged.
            // Vertical improves (800 < 900) → vertical tracker updates independently (SAFM-64).
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);

            event.extendActive(T2, pos1(101), pos2(101), 4.2, 800.0);

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(3.5);
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(0);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(800.0);
            assertThat(event.getMinVerticalSeparationCycleIndex()).isEqualTo(1);
        }

        @Test
        void should_update_min_separation_when_new_cycle_has_smaller_horizontal_separation() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);

            event.extendActive(T2, pos1(101), pos2(101), 2.1, 550.0);

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(2.1);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(550.0);
        }

        @Test
        void should_update_min_separation_cycle_index_to_correct_trajectory_index_when_new_minimum_found() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);

            event.extendActive(T2, pos1(101), pos2(101), 2.1, 550.0);

            // trajectory[1] is the second entry (0-based) — the cycle where minimum was observed
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(1);
        }

        @Test
        void should_not_update_min_separation_cycle_index_when_no_new_minimum_found() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);

            event.extendActive(T2, pos1(101), pos2(101), 4.2, 800.0);

            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(0);
        }

        @Test
        void should_update_min_separation_on_reenter_when_reentry_cycle_has_smaller_horizontal_separation() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);
            event.enterGracePeriod();

            event.reenterActive(T2, pos1(101), pos2(101), 2.1, 400.0);

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(2.1);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(400.0);
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(1);
        }

        @Test
        void should_preserve_min_separation_across_grace_period_when_reentry_has_greater_horizontal_separation() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 2.0, 400.0);
            event.enterGracePeriod();

            event.reenterActive(T2, pos1(101), pos2(101), 3.5, 800.0);

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(2.0);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(400.0);
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(0);
        }

        @Test
        void should_track_global_minimum_across_multiple_active_extend_cycles() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 4.0, 950.0);
            event.extendActive(T2, pos1(101), pos2(101), 1.8, 300.0);  // new minimum at index 1
            event.extendActive(T3, pos1(102), pos2(102), 2.5, 600.0);  // larger — no update

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(1.8);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(300.0);
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(1);
        }

        @Test
        void should_track_min_separation_cycle_index_with_pre_event_offset_across_extend_cycles() {
            // 12 pre-event + creation at index 12 (hSep=4.0)
            // extend at index 13 (hSep=1.8) — new minimum
            // extend at index 14 (hSep=2.5) — no update
            var event = newEventWithPreEventContext(12);
            // creation: minSeparationCycleIndex = 12, hSep = DEFAULT_H_SEP_NM = 3.5
            event.extendActive(T2, pos1(101), pos2(101), 1.8, 300.0);
            event.extendActive(T3, pos1(102), pos2(102), 2.5, 600.0);

            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(1.8);
            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(13);
        }

        // -- SAFM-64: minVerticalSeparationCycleIndex tracking --

        @Test
        void should_initialize_min_vertical_separation_cycle_index_to_zero_when_no_pre_event_context() {
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 3.5, 900.0);

            assertThat(event.getMinVerticalSeparationCycleIndex()).isEqualTo(0);
        }

        @Test
        void should_initialize_min_vertical_separation_cycle_index_offset_by_pre_event_count() {
            var event = newEventWithPreEventContext(12);

            assertThat(event.getMinVerticalSeparationCycleIndex()).isEqualTo(12);
        }

        @Test
        void should_track_min_vertical_separation_cycle_index_at_different_position_than_horizontal_minimum() {
            // AC-2: minSeparationCycleIndex=1 (horiz min), minVerticalSeparationCycleIndex=2 (vert min)
            var event = MinSeparationInfringementEvent.createNew(
                    PAIR, 100L, T1, 3, List.of(), List.of(),
                    pos1(100), pos2(100), 4.0, 950.0);
            event.extendActive(T2, pos1(101), pos2(101), 1.8, 300.0); // new horiz min, not vert min yet
            event.extendActive(T3, pos1(102), pos2(102), 2.5, 200.0); // no horiz update; new vert min

            assertThat(event.getMinSeparationCycleIndex()).isEqualTo(1);
            assertThat(event.getMinVerticalSeparationCycleIndex()).isEqualTo(2);
            assertThat(event.getMinHorizontalSeparationNm()).isEqualTo(1.8);
            assertThat(event.getMinVerticalSeparationFt()).isEqualTo(200.0);
        }
    }
}
