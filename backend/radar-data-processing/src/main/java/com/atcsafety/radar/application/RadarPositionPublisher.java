package com.atcsafety.radar.application;

import com.atcsafety.contracts.RadarPosition;
import com.atcsafety.radar.domain.FlushSentinel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes each validated {@link RadarPosition} to the configured Kafka topic as a
 * {@link RadarPositionMessage}, and emits a flush sentinel after the final cycle.
 *
 * <p>This is an application-layer component — Spring and Kafka imports are permitted
 * here. Domain classes ({@code domain/} package) must not reference this class.
 *
 * <p>The send is fire-and-forget: {@code KafkaTemplate.send()} is called synchronously
 * within the pipeline loop, and the returned {@code CompletableFuture} is wired with an
 * error callback via {@code whenComplete}. Failures are logged at ERROR level and do
 * not halt processing of subsequent positions.
 *
 * <p>Message key for real positions: {@code flightNb} — ensures all positions for the
 * same aircraft land on the same partition, preserving per-aircraft ordering within the
 * single-partition Kafka topic.
 *
 * <p>Message key for the flush sentinel: {@code FlushSentinel.FLIGHT_NB} — consistent with the
 * sentinel {@code flightNb} value; identifies the message unambiguously in logs.
 *
 * <p>Strict cycle ordering is preserved by the sequential loop in
 * {@link RadarProcessingPipeline}: all cycle-N positions are sent before any cycle-N+1
 * position, and {@code send()} is called before the future is awaited (or ignored in
 * fire-and-forget mode). Single-partition FIFO guarantees consumer-side order.
 */
@Service
public class RadarPositionPublisher {

    private static final Logger log = LoggerFactory.getLogger(RadarPositionPublisher.class);

    private final KafkaTemplate<String, RadarPositionMessage> kafkaTemplate;
    private final String kafkaTopic;

    public RadarPositionPublisher(
            KafkaTemplate<String, RadarPositionMessage> kafkaTemplate,
            @Qualifier("kafkaTopic") String kafkaTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaTopic;
    }

    /**
     * Maps the given position to a {@link RadarPositionMessage} and dispatches it to the
     * configured Kafka topic.
     *
     * <p>The {@code flightNb} is used as the Kafka message key so that all positions for
     * the same aircraft are routed to the same partition. Send failures are logged at
     * ERROR level with {@code targetId} and {@code radarCycle} for traceability; the
     * exception is never propagated so the caller continues processing subsequent positions.
     *
     * @param position the validated position to publish; must not be {@code null}
     */
    public void publish(RadarPosition position) {
        RadarPositionMessage message = toMessage(position);

        kafkaTemplate.send(kafkaTopic, position.flightNb(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error(
                                "Failed to publish position to Kafka — targetId={} radarCycle={}: {}",
                                position.targetId(), position.radarCycle(), ex.getMessage());
                    }
                });
    }

    /**
     * Emits a single flush sentinel message after all real positions have been published.
     *
     * <p>The sentinel signals the Detection Service to flush and process the final buffered
     * cycle without needing end-of-topic detection logic (SAD §3). The sentinel is always
     * sent — even when no valid positions were published — to ensure the consumer is never
     * left waiting on an empty buffer.
     *
     * <p>Sentinel field values: {@code targetId=-1}, {@code flightNb=FlushSentinel.FLIGHT_NB},
     * {@code radarCycle=lastCycle+1}. Numeric fields irrelevant to the sentinel are set
     * to {@code 0.0f}; optional reference fields are {@code null}.
     *
     * <p>Send failures are logged at ERROR level and not propagated, consistent with
     * {@link #publish(RadarPosition)}.
     *
     * @param lastCycle the highest {@code radarCycle} value that was successfully published;
     *                  {@code 0} when no valid positions were published
     */
    public void publishFlush(int lastCycle) {
        RadarPositionMessage flushMessage = new RadarPositionMessage(
                -1,
                lastCycle + 1,
                FlushSentinel.FLIGHT_NB,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0,
                null,
                null,
                null
        );

        kafkaTemplate.send(kafkaTopic, FlushSentinel.FLIGHT_NB, flushMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error(
                                "Failed to publish flush sentinel to Kafka — radarCycle={}: {}",
                                lastCycle + 1, ex.getMessage());
                    }
                });
    }

    private static RadarPositionMessage toMessage(RadarPosition position) {
        return new RadarPositionMessage(
                position.targetId(),
                position.radarCycle(),
                position.flightNb(),
                position.x(),
                position.y(),
                position.altFeet(),
                position.speedKn(),
                position.headingDeg(),
                position.lat(),
                position.lon(),
                position.timestampUTC()
        );
    }
}
