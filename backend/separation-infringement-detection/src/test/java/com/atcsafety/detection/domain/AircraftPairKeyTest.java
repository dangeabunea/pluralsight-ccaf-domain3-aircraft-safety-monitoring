package com.atcsafety.detection.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AircraftPairKey}.
 *
 * <p>Pure Java — no Spring context, no Kafka, no MongoDB.
 */
class AircraftPairKeyTest {

    @Nested
    class Construction {

        @Test
        void should_place_lexicographically_smaller_callsign_first() {
            var key = AircraftPairKey.of("BA123", "AF456");

            assertThat(key.callsign1()).isEqualTo("AF456");
            assertThat(key.callsign2()).isEqualTo("BA123");
        }

        @Test
        void should_preserve_order_when_first_callsign_is_already_smaller() {
            var key = AircraftPairKey.of("AF456", "BA123");

            assertThat(key.callsign1()).isEqualTo("AF456");
            assertThat(key.callsign2()).isEqualTo("BA123");
        }

        @Test
        void should_produce_equal_keys_regardless_of_argument_order() {
            var keyAB = AircraftPairKey.of("BA123", "AF456");
            var keyBA = AircraftPairKey.of("AF456", "BA123");

            assertThat(keyAB).isEqualTo(keyBA);
        }

        @Test
        void should_produce_equal_hash_codes_regardless_of_argument_order() {
            var keyAB = AircraftPairKey.of("BA123", "AF456");
            var keyBA = AircraftPairKey.of("AF456", "BA123");

            assertThat(keyAB.hashCode()).isEqualTo(keyBA.hashCode());
        }

        @Test
        void should_distinguish_different_pairs() {
            var keyAB = AircraftPairKey.of("AF456", "BA123");
            var keyAC = AircraftPairKey.of("AF456", "KL007");

            assertThat(keyAB).isNotEqualTo(keyAC);
        }
    }

    @Nested
    class AsString {

        @Test
        void should_return_canonical_string_with_hyphen_separator() {
            var key = AircraftPairKey.of("BA123", "AF456");

            assertThat(key.asString()).isEqualTo("AF456-BA123");
        }

        @Test
        void should_produce_same_string_regardless_of_construction_order() {
            var keyAB = AircraftPairKey.of("BA123", "AF456");
            var keyBA = AircraftPairKey.of("AF456", "BA123");

            assertThat(keyAB.asString()).isEqualTo(keyBA.asString());
        }
    }
}
