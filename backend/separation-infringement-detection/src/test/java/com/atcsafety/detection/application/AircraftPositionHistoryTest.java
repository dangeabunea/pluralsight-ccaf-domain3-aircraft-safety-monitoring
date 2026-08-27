package com.atcsafety.detection.application;

import com.atcsafety.detection.domain.TrajectoryPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AircraftPositionHistory}.
 *
 * <p>Covers rolling eviction at the buffer capacity, cold-start (empty buffer),
 * snapshot immutability, multi-callsign independence, and edge cases.
 *
 * <p>Pure Java — no Spring context.
 */
class AircraftPositionHistoryTest {

    private static final Instant T = Instant.parse("2026-03-01T10:00:00Z");
    private static final int MAX_HISTORY = 12;

    private AircraftPositionHistory history;

    @BeforeEach
    void setUp() {
        history = new AircraftPositionHistory(MAX_HISTORY);
    }

    // ------------------------------------------------------------------
    // Test factory — keeps construction in one place so that if the
    // TrajectoryPosition.from() signature changes only this method needs
    // updating across the entire test class.
    // ------------------------------------------------------------------

    private static TrajectoryPosition position(String callsign, long cycle) {
        return TrajectoryPosition.from(callsign, (float) cycle * 10, 0.0f, 20000.0f, 0.0f,
                0, null, null, cycle, T);
    }

    @Nested
    class GetSnapshot {

        @Test
        void should_return_empty_list_when_no_positions_recorded_for_callsign() {
            List<TrajectoryPosition> snapshot = history.getSnapshot("UNKNOWN");

            assertThat(snapshot).isEmpty();
        }

        @Test
        void should_return_single_position_after_one_update_for_callsign() {
            var pos = position("BA123", 1L);

            history.update(pos);

            assertThat(history.getSnapshot("BA123")).containsExactly(pos);
        }

        @Test
        void should_return_positions_in_insertion_order() {
            var p1 = position("BA123", 1L);
            var p2 = position("BA123", 2L);
            var p3 = position("BA123", 3L);

            history.update(p1);
            history.update(p2);
            history.update(p3);

            assertThat(history.getSnapshot("BA123")).containsExactly(p1, p2, p3);
        }

        @Test
        void should_return_snapshot_up_to_max_history_size_before_eviction() {
            for (long i = 1; i <= MAX_HISTORY; i++) {
                history.update(position("BA123", i));
            }

            assertThat(history.getSnapshot("BA123")).hasSize(MAX_HISTORY);
        }

        @Test
        void should_evict_oldest_position_when_buffer_exceeds_max_history_size() {
            var oldest = position("BA123", 1L);
            history.update(oldest);

            // Fill to capacity + 1 to trigger eviction
            for (long i = 2; i <= MAX_HISTORY + 1; i++) {
                history.update(position("BA123", i));
            }

            List<TrajectoryPosition> snapshot = history.getSnapshot("BA123");
            assertThat(snapshot).hasSize(MAX_HISTORY);
            assertThat(snapshot).doesNotContain(oldest);
        }

        @Test
        void should_retain_most_recent_positions_after_eviction() {
            // Insert MAX_HISTORY+3 positions — only the last MAX_HISTORY should remain
            for (long i = 1; i <= MAX_HISTORY + 3; i++) {
                history.update(position("BA123", i));
            }

            List<TrajectoryPosition> snapshot = history.getSnapshot("BA123");
            // First position in snapshot should be cycle 4 (MAX_HISTORY+3 - MAX_HISTORY + 1)
            assertThat(snapshot.get(0).radarCycle()).isEqualTo(4L);
            assertThat(snapshot.get(snapshot.size() - 1).radarCycle()).isEqualTo((long) MAX_HISTORY + 3);
        }

        @Test
        void should_return_immutable_snapshot_that_does_not_reflect_subsequent_updates() {
            history.update(position("BA123", 1L));
            List<TrajectoryPosition> snapshot = history.getSnapshot("BA123");

            history.update(position("BA123", 2L));

            // The earlier snapshot must still have size 1
            assertThat(snapshot).hasSize(1);
        }

        @Test
        void should_return_unmodifiable_snapshot_list() {
            history.update(position("BA123", 1L));
            List<TrajectoryPosition> snapshot = history.getSnapshot("BA123");

            assertThatThrownBy(() -> snapshot.add(position("BA123", 99L)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Update {

        @Test
        void should_track_positions_for_multiple_callsigns_independently() {
            var p1 = position("BA123", 10L);
            var p2 = position("AF456", 10L);

            history.update(p1);
            history.update(p2);

            assertThat(history.getSnapshot("BA123")).containsExactly(p1);
            assertThat(history.getSnapshot("AF456")).containsExactly(p2);
        }

        @Test
        void should_not_affect_other_callsigns_when_eviction_occurs_for_one_callsign() {
            // Fill BA123 beyond capacity
            for (long i = 1; i <= MAX_HISTORY + 1; i++) {
                history.update(position("BA123", i));
            }
            // AF456 has only one position
            var af456pos = position("AF456", 1L);
            history.update(af456pos);

            assertThat(history.getSnapshot("AF456")).containsExactly(af456pos);
        }

        @Test
        void should_handle_exactly_max_history_positions_without_eviction() {
            for (long i = 1; i <= MAX_HISTORY; i++) {
                history.update(position("BA123", i));
            }

            // Snapshot size equals capacity — no eviction yet
            assertThat(history.getSnapshot("BA123")).hasSize(MAX_HISTORY);
            // First position is still cycle 1
            assertThat(history.getSnapshot("BA123").get(0).radarCycle()).isEqualTo(1L);
        }

        @Test
        void should_evict_oldest_entry_precisely_on_the_cycle_after_capacity_is_reached() {
            // Fill to capacity
            for (long i = 1; i <= MAX_HISTORY; i++) {
                history.update(position("BA123", i));
            }
            var firstPos = history.getSnapshot("BA123").get(0);

            // Adding one more triggers eviction
            history.update(position("BA123", (long) MAX_HISTORY + 1));

            assertThat(history.getSnapshot("BA123")).doesNotContain(firstPos);
        }
    }

    @Nested
    class ColdStart {

        @Test
        void should_return_empty_snapshot_for_all_callsigns_on_fresh_instance() {
            assertThat(history.getSnapshot("BA123")).isEmpty();
            assertThat(history.getSnapshot("AF456")).isEmpty();
        }

        @Test
        void should_return_partial_snapshot_when_fewer_than_max_positions_have_been_recorded() {
            history.update(position("BA123", 1L));
            history.update(position("BA123", 2L));

            assertThat(history.getSnapshot("BA123")).hasSize(2);
        }
    }

    @Nested
    class EvictStaleTracks {

        @Test
        void should_remove_callsign_entry_after_3_consecutive_missed_cycles() {
            history.update(position("BA123", 1L));

            // Three cycles pass with no update for BA123
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());

            assertThat(history.getSnapshot("BA123")).isEmpty();
        }

        @Test
        void should_not_remove_entry_before_3_missed_cycles() {
            history.update(position("BA123", 1L));

            // Only two cycles missed — not yet at threshold
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());

            assertThat(history.getSnapshot("BA123")).hasSize(1);
        }

        @Test
        void should_reset_counter_when_position_update_arrives_after_2_missed_cycles() {
            history.update(position("BA123", 1L));

            // Two missed cycles — counter reaches 2
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());

            // Update arrives — counter resets
            history.update(position("BA123", 4L));
            history.evictStaleTracks(Set.of("BA123"));

            // After reset, another two missed cycles should not evict
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());

            assertThat(history.getSnapshot("BA123")).isNotEmpty();
        }

        @Test
        void should_create_fresh_entry_when_evicted_callsign_reappears() {
            history.update(position("BA123", 1L));

            // Evict after 3 missed cycles
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());

            assertThat(history.getSnapshot("BA123")).isEmpty();

            // Callsign reappears — fresh entry with only the new position
            history.update(position("BA123", 5L));

            assertThat(history.getSnapshot("BA123")).hasSize(1);
            assertThat(history.getSnapshot("BA123").get(0).radarCycle()).isEqualTo(5L);
        }

        @Test
        void should_not_remove_entry_for_callsign_present_in_active_set() {
            history.update(position("BA123", 1L));

            // Three cycles, but BA123 is always in the active set — counter keeps resetting
            history.evictStaleTracks(Set.of("BA123"));
            history.evictStaleTracks(Set.of("BA123"));
            history.evictStaleTracks(Set.of("BA123"));

            assertThat(history.getSnapshot("BA123")).hasSize(1);
        }

        @Test
        void should_evict_multiple_stale_callsigns_in_same_cycle() {
            history.update(position("BA123", 1L));
            history.update(position("AF456", 1L));
            history.update(position("LH789", 1L));

            // All three miss 3 consecutive cycles
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());
            history.evictStaleTracks(Set.of());

            assertThat(history.getSnapshot("BA123")).isEmpty();
            assertThat(history.getSnapshot("AF456")).isEmpty();
            assertThat(history.getSnapshot("LH789")).isEmpty();
        }

        @Test
        void should_not_affect_active_callsign_history_when_stale_callsign_is_evicted() {
            history.update(position("BA123", 1L));
            history.update(position("AF456", 1L));

            // AF456 is active; BA123 is stale for 3 cycles
            history.evictStaleTracks(Set.of("AF456"));
            history.evictStaleTracks(Set.of("AF456"));
            history.evictStaleTracks(Set.of("AF456"));

            assertThat(history.getSnapshot("BA123")).isEmpty();
            assertThat(history.getSnapshot("AF456")).hasSize(1);
        }
    }
}
