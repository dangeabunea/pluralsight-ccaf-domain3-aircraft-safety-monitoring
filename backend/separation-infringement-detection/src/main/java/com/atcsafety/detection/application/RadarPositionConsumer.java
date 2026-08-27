package com.atcsafety.detection.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes {@link RadarPositionMessage} records from the {@code radar.validated-positions}
 * Kafka topic, buffers them by radar cycle, and dispatches each completed cycle to
 * {@link CycleDetectionProcessor} for separation infringement analysis.
 *
 * <h2>Cycle boundary detection</h2>
 * <p>There is no explicit end-of-cycle marker in the message stream. A cycle is
 * considered complete when the first message of the next cycle arrives — identified
 * by a change in {@code radarCycle}. At that point the current buffer is dispatched
 * and cleared, and the boundary-triggering message begins the new cycle's buffer.
 *
 * <h2>Flush sentinel</h2>
 * <p>A message with {@code flightNb = "__FLUSH__"} (identified via
 * {@link RadarPositionMessage#isFlushSentinel()}) signals end-of-dump. The sentinel
 * check runs as the <strong>first operation</strong> in {@link #onMessage} — before any
 * buffer access — to prevent the sentinel from being treated as a regular position.
 * On flush, the final buffered cycle is dispatched and the buffer is cleared.
 *
 * <h2>Thread safety</h2>
 * <p>Not thread-safe. Single-threaded use is assumed: exactly one Kafka consumer
 * thread in the {@code detection-service} consumer group processes these messages
 * sequentially. The single-thread assumption is load-bearing for the correctness
 * of {@code currentCycle} and {@code cycleBuffer} state — no locking is applied.
 *
 * <h2>Kafka wiring</h2>
 * <p>Uses Spring Boot's auto-configured {@code kafkaListenerContainerFactory}. Deserializer
 * type and header settings are configured via {@code spring.kafka.consumer.properties.*}
 * in {@code application.properties}, keeping all Kafka config out of Java code.
 */
@Component
public class RadarPositionConsumer {

    private static final Logger log = LoggerFactory.getLogger(RadarPositionConsumer.class);

    private final CycleDetectionProcessor processor;
    private int currentCycle = -1;
    private final List<RadarPositionMessage> cycleBuffer = new ArrayList<>();

    RadarPositionConsumer(CycleDetectionProcessor processor) {
        this.processor = processor;
    }

    /**
     * Processes one incoming Kafka message.
     *
     * <p>The flush sentinel check is the first operation — before any buffer access.
     * For regular messages, a cycle boundary is detected when {@code message.radarCycle()}
     * differs from {@code currentCycle}; the buffered cycle is dispatched and cleared
     * before the new message is added.
     *
     * @param message the deserialized position message or flush sentinel
     */
    @KafkaListener(topics = "#{@detectionKafkaTopic}")
    public void onMessage(RadarPositionMessage message) {
        // Flush sentinel check MUST be first — before any buffer access (per spec note in SAFM-38)
        if (message.isFlushSentinel()) {
            log.info("Flush sentinel received — dispatching final buffered cycle {}", currentCycle);
            dispatchCurrentCycleIfBuffered();
            return;
        }

        if (currentCycle != -1 && message.radarCycle() != currentCycle) {
            log.debug("Cycle boundary: dispatching cycle {} ({} positions buffered)",
                    currentCycle, cycleBuffer.size());
            dispatchCurrentCycleIfBuffered();
        }

        currentCycle = message.radarCycle();
        cycleBuffer.add(message);
    }

    private void dispatchCurrentCycleIfBuffered() {
        if (!cycleBuffer.isEmpty()) {
            processor.processCompletedCycle(new ArrayList<>(cycleBuffer), currentCycle);
            cycleBuffer.clear();
        }
    }
}
