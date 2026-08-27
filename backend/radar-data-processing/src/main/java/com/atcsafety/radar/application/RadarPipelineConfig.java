package com.atcsafety.radar.application;

import com.atcsafety.radar.enrichment.CoordinateDeriver;
import com.atcsafety.radar.validation.RadarPositionValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the radar data processing pipeline.
 *
 * <p>Declares beans for the pure-Java pipeline components that cannot be
 * annotated directly (they carry zero Spring imports by design).
 */
@Configuration
public class RadarPipelineConfig {

    /**
     * Validator for radar positions — checks identity (flightNb) and coordinate range.
     */
    @Bean
    public RadarPositionValidator radarPositionValidator() {
        return new RadarPositionValidator();
    }

    /**
     * Derives missing {@code lat}/{@code lon} from cartesian coordinates using the
     * radar installation's geographic position, loaded from {@code application.properties}.
     *
     * <p>Both {@code radar.lat} and {@code radar.lon} are required. This method performs
     * an explicit null-check on each value and fails fast with a descriptive error message
     * naming the missing property — the application will not start without them.
     *
     * <p>The {@link RadarConfig} bean carries the validated coordinates; this method
     * passes them to the pure-Java {@link CoordinateDeriver}, keeping framework
     * dependencies out of the domain class.
     *
     * @throws IllegalStateException if {@code radar.lat} or {@code radar.lon} is absent
     */
    @Bean
    public CoordinateDeriver coordinateDeriver(RadarConfig radarConfig) {
        if (radarConfig.lat() == null) {
            throw new IllegalStateException(
                    "Required configuration property 'radar.lat' is not set. "
                    + "Add 'radar.lat=<degrees>' to application.properties.");
        }
        if (radarConfig.lon() == null) {
            throw new IllegalStateException(
                    "Required configuration property 'radar.lon' is not set. "
                    + "Add 'radar.lon=<degrees>' to application.properties.");
        }
        if (radarConfig.lat() < -90 || radarConfig.lat() > 90) {
            throw new IllegalStateException(
                    "Invalid radar.lat: " + radarConfig.lat() + " \u2014 must be in range [-90, 90]");
        }
        if (radarConfig.lon() < -180 || radarConfig.lon() > 180) {
            throw new IllegalStateException(
                    "Invalid radar.lon: " + radarConfig.lon() + " \u2014 must be in range [-180, 180]");
        }
        return new CoordinateDeriver(radarConfig.lat(), radarConfig.lon());
    }

    /**
     * Validates the Kafka topic name and exposes it as a named bean.
     *
     * <p>The topic name is sourced from {@code radar.kafka.topic} in
     * {@code application.properties}. If the property is absent or blank, the application
     * will refuse to start with a descriptive error naming the missing property.
     *
     * <p>The returned {@code String} bean is the injection point for
     * {@code RadarPositionPublisher} (SAFM-22/23) — it injects the topic name via
     * {@code @Qualifier("kafkaTopic")} rather than hard-coding it.
     *
     * @throws IllegalStateException if {@code radar.kafka.topic} is absent or blank
     */
    @Bean("kafkaTopic")
    public String kafkaTopic(KafkaProducerConfig kafkaProducerConfig) {
        if (kafkaProducerConfig.topic() == null || kafkaProducerConfig.topic().isBlank()) {
            throw new IllegalStateException(
                    "Required configuration property 'radar.kafka.topic' is not set. "
                    + "Add 'radar.kafka.topic=<topic-name>' to application.properties.");
        }
        return kafkaProducerConfig.topic();
    }

    /**
     * Selects the correct {@link InterCycleDelay} implementation based on
     * {@code radar.processing.inter-cycle-delay-ms}.
     *
     * <ul>
     *   <li>Absent or zero → {@link NoOpDelay}: maximum-speed processing for tests and demos.
     *   <li>Positive → {@link ThreadSleepDelay}: real-time cadence matching live SSR radar.
     *   <li>Negative → {@link IllegalStateException}: the application refuses to start.
     * </ul>
     *
     * <p>Override the property via the {@code RADAR_PROCESSING_INTER_CYCLE_DELAY_MS}
     * environment variable (Spring relaxed binding).
     *
     * @throws IllegalStateException if {@code radar.processing.inter-cycle-delay-ms} is negative
     */
    @Bean
    public InterCycleDelay interCycleDelay(ProcessingConfig processingConfig) {
        int delayMs = processingConfig.interCycleDelayMs();
        if (delayMs < 0) {
            throw new IllegalStateException(
                    "Invalid configuration: 'radar.processing.inter-cycle-delay-ms' must be >= 0, "
                    + "got " + delayMs + ". Remove the property or set it to 0 for zero-wait mode.");
        }
        return delayMs == 0 ? new NoOpDelay() : new ThreadSleepDelay(delayMs);
    }
}
