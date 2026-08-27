package com.atcsafety.radar.application;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaProducerConfig}.
 *
 * <p>These tests verify the record-level contract: that the value passed to the
 * constructor is accessible via the {@code topic()} accessor. No Spring context needed.
 */
class KafkaProducerConfigTest {

    @Nested
    class Construction {

        @Test
        void should_hold_topic_value_when_constructed() {
            KafkaProducerConfig config = new KafkaProducerConfig("radar.validated-positions");

            assertThat(config.topic()).isEqualTo("radar.validated-positions");
        }
    }
}
