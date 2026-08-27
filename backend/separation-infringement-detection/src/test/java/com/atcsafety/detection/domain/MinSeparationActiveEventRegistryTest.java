package com.atcsafety.detection.domain;

import com.atcsafety.detection.application.InMemoryMinSeparationActiveEventRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MinSeparationActiveEventRegistry} via its in-memory implementation.
 *
 * <p>Tests the 5-method contract: contains, get, register, remove, activeKeys.
 * Pure Java — no Spring context.
 */
class MinSeparationActiveEventRegistryTest {

    private MinSeparationActiveEventRegistry registry;

    private static final Instant T1 = Instant.parse("2026-03-01T10:00:00Z");
    private static final AircraftPairKey PAIR_AB = AircraftPairKey.of("BA123", "AF456");
    private static final AircraftPairKey PAIR_CD = AircraftPairKey.of("KL007", "LH400");

    private static TrajectoryPosition pos(String callsign) {
        return TrajectoryPosition.from(callsign, 1000.0f, 2000.0f, 20000.0f, 0.0f, 0, null, null, 100L, T1);
    }

    private MinSeparationInfringementEvent newEvent(AircraftPairKey pair) {
        return MinSeparationInfringementEvent.createNew(
                pair, 100L, T1, 3, java.util.List.of(), java.util.List.of(),
                pos(pair.callsign1()), pos(pair.callsign2()), 0.0, 0.0);
    }

    @BeforeEach
    void setUp() {
        registry = new InMemoryMinSeparationActiveEventRegistry();
    }

    @Nested
    class Contains {

        @Test
        void should_return_false_when_pair_not_registered() {
            assertThat(registry.contains(PAIR_AB)).isFalse();
        }

        @Test
        void should_return_true_after_registering_pair() {
            registry.register(PAIR_AB, newEvent(PAIR_AB));

            assertThat(registry.contains(PAIR_AB)).isTrue();
        }

        @Test
        void should_return_false_after_removing_pair() {
            registry.register(PAIR_AB, newEvent(PAIR_AB));
            registry.remove(PAIR_AB);

            assertThat(registry.contains(PAIR_AB)).isFalse();
        }
    }

    @Nested
    class Get {

        @Test
        void should_return_registered_event_for_pair() {
            var event = newEvent(PAIR_AB);
            registry.register(PAIR_AB, event);

            assertThat(registry.get(PAIR_AB)).isSameAs(event);
        }

        @Test
        void should_return_null_when_pair_not_registered() {
            assertThat(registry.get(PAIR_AB)).isNull();
        }
    }

    @Nested
    class Register {

        @Test
        void should_make_pair_findable_after_registration() {
            var event = newEvent(PAIR_AB);
            registry.register(PAIR_AB, event);

            assertThat(registry.contains(PAIR_AB)).isTrue();
            assertThat(registry.get(PAIR_AB)).isSameAs(event);
        }

        @Test
        void should_register_multiple_pairs_independently() {
            registry.register(PAIR_AB, newEvent(PAIR_AB));
            registry.register(PAIR_CD, newEvent(PAIR_CD));

            assertThat(registry.contains(PAIR_AB)).isTrue();
            assertThat(registry.contains(PAIR_CD)).isTrue();
        }
    }

    @Nested
    class Remove {

        @Test
        void should_return_removed_event() {
            var event = newEvent(PAIR_AB);
            registry.register(PAIR_AB, event);

            var removed = registry.remove(PAIR_AB);

            assertThat(removed).isSameAs(event);
        }

        @Test
        void should_not_affect_other_pairs_when_removing_one() {
            registry.register(PAIR_AB, newEvent(PAIR_AB));
            registry.register(PAIR_CD, newEvent(PAIR_CD));

            registry.remove(PAIR_AB);

            assertThat(registry.contains(PAIR_AB)).isFalse();
            assertThat(registry.contains(PAIR_CD)).isTrue();
        }
    }

    @Nested
    class ActiveKeys {

        @Test
        void should_return_empty_collection_when_no_events_registered() {
            assertThat(registry.activeKeys()).isEmpty();
        }

        @Test
        void should_return_all_registered_pair_keys() {
            registry.register(PAIR_AB, newEvent(PAIR_AB));
            registry.register(PAIR_CD, newEvent(PAIR_CD));

            assertThat(registry.activeKeys()).containsExactlyInAnyOrder(PAIR_AB, PAIR_CD);
        }

        @Test
        void should_not_include_removed_pairs() {
            registry.register(PAIR_AB, newEvent(PAIR_AB));
            registry.register(PAIR_CD, newEvent(PAIR_CD));
            registry.remove(PAIR_AB);

            assertThat(registry.activeKeys()).containsExactly(PAIR_CD);
        }

        @Test
        void should_return_snapshot_not_affected_by_subsequent_registration() {
            var pair1 = AircraftPairKey.of("AA100", "BB200");
            var pair2 = AircraftPairKey.of("CC300", "DD400");
            var pos1a = TrajectoryPosition.from("AA100", 1.0f, 2.0f, 20000.0f, 0.0f, 0, null, null, 1L, T1);
            var pos1b = TrajectoryPosition.from("BB200", 1.5f, 2.5f, 21000.0f, 0.0f, 0, null, null, 1L, T1);
            var pos2a = TrajectoryPosition.from("CC300", 1.0f, 2.0f, 20000.0f, 0.0f, 0, null, null, 1L, T1);
            var pos2b = TrajectoryPosition.from("DD400", 1.5f, 2.5f, 21000.0f, 0.0f, 0, null, null, 1L, T1);
            var event1 = MinSeparationInfringementEvent.createNew(pair1, 1L, T1, 3, java.util.List.of(), java.util.List.of(), pos1a, pos1b, 0.0, 0.0);
            var event2 = MinSeparationInfringementEvent.createNew(pair2, 1L, T1, 3, java.util.List.of(), java.util.List.of(), pos2a, pos2b, 0.0, 0.0);

            registry.register(pair1, event1);
            var snapshot = registry.activeKeys();      // snapshot before second registration
            registry.register(pair2, event2);           // mutate registry after snapshot

            assertThat(snapshot).hasSize(1).containsExactly(pair1);
        }
    }
}
