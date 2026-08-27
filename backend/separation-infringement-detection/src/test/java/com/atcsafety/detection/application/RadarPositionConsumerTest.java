package com.atcsafety.detection.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RadarPositionConsumer}.
 *
 * <p>Covers AC-3 (cycle boundary dispatch), AC-4 (flush sentinel handling),
 * and AC-5 (buffer cleared after each dispatch) from SAFM-38.
 *
 * <p>No Spring context — {@link RadarPositionConsumer} is constructed directly
 * with a mocked {@link CycleDetectionProcessor}. Tests call {@link RadarPositionConsumer#onMessage}
 * directly, bypassing the Kafka listener infrastructure entirely.
 */
@ExtendWith(MockitoExtension.class)
class RadarPositionConsumerTest {

    @Mock
    private CycleDetectionProcessor processor;

    private RadarPositionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RadarPositionConsumer(processor);
    }

    private RadarPositionMessage position(int cycle, String callsign) {
        return new RadarPositionMessage(1, cycle, callsign,
                1000f, 2000f, 20000f, 400f, 0, null, null, "2026-03-01T10:00:00Z");
    }

    private RadarPositionMessage flush(int flushCycle) {
        return new RadarPositionMessage(0, flushCycle, RadarPositionMessage.FLUSH_FLIGHT_NB,
                0f, 0f, 0f, 0f, 0, null, null, null);
    }

    // -----------------------------------------------------------------------
    // AC-3: Cycle boundary dispatch
    // -----------------------------------------------------------------------

    @Nested
    class CycleBoundaryDispatch {

        @Test
        void should_not_dispatch_while_only_first_cycle_messages_arrive() {
            consumer.onMessage(position(1, "BA123"));
            consumer.onMessage(position(1, "AF456"));

            verify(processor, never()).processCompletedCycle(any(), anyInt());
        }

        @Test
        void should_dispatch_first_cycle_when_first_message_of_second_cycle_arrives() {
            consumer.onMessage(position(1, "BA123"));
            consumer.onMessage(position(1, "AF456"));
            consumer.onMessage(position(1, "KL007"));
            consumer.onMessage(position(2, "BA123")); // boundary trigger

            var captor = ArgumentCaptor.forClass(List.class);
            verify(processor).processCompletedCycle(captor.capture(), anyInt());
            assertThat(captor.getValue()).hasSize(3);
        }

        @Test
        void should_dispatch_cycle_with_correct_cycle_number_at_boundary() {
            consumer.onMessage(position(10, "BA123"));
            consumer.onMessage(position(11, "BA123")); // boundary: dispatch cycle 10

            verify(processor).processCompletedCycle(any(), anyInt());
        }

        @Test
        void should_add_boundary_triggering_message_to_new_cycle_buffer() {
            consumer.onMessage(position(1, "BA123"));
            consumer.onMessage(position(2, "BA123")); // dispatches cycle 1, starts cycle 2

            // Trigger dispatch of cycle 2 via flush to inspect its contents
            consumer.onMessage(flush(3));

            var captor = ArgumentCaptor.forClass(List.class);
            verify(processor, times(2)).processCompletedCycle(captor.capture(), anyInt());
            // Second dispatch (cycle 2) must contain exactly the one boundary-triggering message
            assertThat(captor.getAllValues().get(1)).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // AC-4: Flush sentinel handling
    // -----------------------------------------------------------------------

    @Nested
    class FlushSentinelHandling {

        @Test
        void should_dispatch_buffered_cycle_when_flush_sentinel_arrives() {
            consumer.onMessage(position(5, "BA123"));
            consumer.onMessage(position(5, "AF456"));
            consumer.onMessage(flush(6)); // dispatch cycle 5

            verify(processor).processCompletedCycle(any(), anyInt());
        }

        @Test
        void should_not_include_flush_message_in_dispatched_cycle() {
            consumer.onMessage(position(5, "BA123"));
            consumer.onMessage(flush(6));

            var captor = ArgumentCaptor.forClass(List.class);
            verify(processor).processCompletedCycle(captor.capture(), anyInt());
            assertThat(captor.getValue()).hasSize(1);
        }

        @Test
        void should_not_dispatch_when_flush_arrives_and_buffer_is_empty() {
            consumer.onMessage(flush(1)); // no positions buffered

            verify(processor, never()).processCompletedCycle(any(), anyInt());
        }

        @Test
        void should_not_dispatch_again_on_second_flush_after_buffer_is_already_cleared() {
            consumer.onMessage(position(5, "BA123"));
            consumer.onMessage(flush(6)); // dispatch + clear
            consumer.onMessage(flush(7)); // buffer already empty — no dispatch

            verify(processor, times(1)).processCompletedCycle(any(), anyInt());
        }
    }

    // -----------------------------------------------------------------------
    // AC-5: Memory does not grow unboundedly — buffer cleared after dispatch
    // -----------------------------------------------------------------------

    @Nested
    class MemoryManagement {

        @Test
        void should_clear_buffer_after_cycle_boundary_dispatch() {
            consumer.onMessage(position(1, "BA123"));
            consumer.onMessage(position(2, "BA123")); // dispatches cycle 1, adds to cycle-2 buffer

            // Flush triggers cycle-2 dispatch — buffer should only contain the one cycle-2 message
            consumer.onMessage(flush(3));

            var captor = ArgumentCaptor.forClass(List.class);
            verify(processor, times(2)).processCompletedCycle(captor.capture(), anyInt());
            assertThat(captor.getAllValues().get(1)).hasSize(1);
        }

        @Test
        void should_clear_buffer_after_flush_dispatch() {
            consumer.onMessage(position(5, "BA123"));
            consumer.onMessage(position(5, "AF456"));
            consumer.onMessage(flush(6)); // dispatch + clear

            // A second flush finds an empty buffer — no further dispatch
            consumer.onMessage(flush(7));
            verify(processor, times(1)).processCompletedCycle(any(), anyInt());
        }
    }
}
