package com.atcsafety.detection.application;

import com.atcsafety.detection.domain.InfringementDetector;
import com.atcsafety.detection.domain.MinSeparationActiveEventRegistry;
import com.atcsafety.detection.domain.MinSeparationEventLifecycleManager;
import com.atcsafety.detection.domain.MinSeparationEventStore;
import com.atcsafety.detection.domain.SeparationCalculator;
import com.atcsafety.detection.domain.SeparationThresholds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Separation Infringement Detection Service.
 *
 * <p>Bridges externalized configuration ({@link DetectionConfig}) into
 * pure-Java domain objects and named beans. No domain class carries a Spring
 * annotation — this class owns the framework boundary.
 *
 * <p>Startup guards here follow the same fail-fast pattern used by
 * {@code RadarPipelineConfig} in the Radar Data Processing Service:
 * missing or invalid properties throw {@link IllegalStateException} with a
 * descriptive message naming the offending property, so operators can identify
 * and fix misconfiguration immediately.
 */
@Configuration
public class DetectionApplicationConfig {

    private static final Logger log = LoggerFactory.getLogger(DetectionApplicationConfig.class);

    private static final double DEFAULT_HORIZONTAL_THRESHOLD_NM = 5.0;
    private static final int DEFAULT_VERTICAL_THRESHOLD_FT = 1000;
    private static final int DEFAULT_GRACE_PERIOD_CYCLES = 3;
    private static final int DEFAULT_OBSERVATION_WINDOW_CYCLES = 12;

    /**
     * Creates a {@link SeparationThresholds} bean from externalized configuration.
     *
     * <p>If {@code detection.horizontalThresholdNm} is absent, the default value of
     * 5.0 NM is used and a WARN is logged.
     * If {@code detection.verticalThresholdFt} is absent, the default value of
     * 1000 ft is used and a WARN is logged.
     *
     * @throws IllegalStateException never — both thresholds have safe defaults
     */
    @Bean
    public SeparationThresholds separationThresholds(DetectionConfig config) {
        double horizontal;
        if (config.horizontalThresholdNm() == null) {
            log.warn("detection.horizontalThresholdNm not configured -- using default: {} NM",
                    DEFAULT_HORIZONTAL_THRESHOLD_NM);
            horizontal = DEFAULT_HORIZONTAL_THRESHOLD_NM;
        } else {
            log.info("Horizontal separation threshold configured: {} NM",
                    config.horizontalThresholdNm());
            horizontal = config.horizontalThresholdNm();
        }

        int vertical;
        if (config.verticalThresholdFt() == null) {
            log.warn("detection.verticalThresholdFt not configured -- using default: {} ft",
                    DEFAULT_VERTICAL_THRESHOLD_FT);
            vertical = DEFAULT_VERTICAL_THRESHOLD_FT;
        } else {
            log.info("Vertical separation threshold configured: {} ft",
                    config.verticalThresholdFt());
            vertical = config.verticalThresholdFt();
        }

        return new SeparationThresholds(horizontal, vertical);
    }

    /**
     * Validates and exposes the grace-period cycle count as a named bean.
     *
     * <p>If {@code detection.gracePeriodCycles} is absent, 3 cycles are used
     * and a WARN is logged. A value below 1 fails startup immediately —
     * a grace period of zero cycles is operationally meaningless and would
     * bypass the {@code ACTIVE → GRACE_PERIOD → CLOSED} state machine entirely.
     *
     * @throws IllegalStateException if {@code detection.gracePeriodCycles} is set
     *                               to a value less than 1
     */
    @Bean
    public Integer gracePeriodCycles(DetectionConfig config) {
        if (config.gracePeriodCycles() == null) {
            log.warn("detection.gracePeriodCycles not configured -- using default: {} cycles",
                    DEFAULT_GRACE_PERIOD_CYCLES);
            return DEFAULT_GRACE_PERIOD_CYCLES;
        }
        if (config.gracePeriodCycles() < 1) {
            throw new IllegalStateException(
                    "Invalid detection.gracePeriodCycles: " + config.gracePeriodCycles()
                    + " -- must be >= 1.");
        }
        log.info("Grace period configured: {} cycles", config.gracePeriodCycles());
        return config.gracePeriodCycles();
    }

    /**
     * Validates and exposes the Kafka consumer topic name as a named bean.
     *
     * <p>The topic is sourced from {@code detection.kafka.topic}. It is required —
     * the service cannot function without a topic to consume from.
     *
     * @throws IllegalStateException if {@code detection.kafka.topic} is absent or blank
     */
    @Bean("detectionKafkaTopic")
    public String detectionKafkaTopic(DetectionConfig config) {
        String topic = config.kafka() != null ? config.kafka().topic() : null;
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException(
                    "Required configuration property 'detection.kafka.topic' is not set. "
                    + "Add 'detection.kafka.topic=<topic-name>' to application.properties.");
        }
        log.info("Detection service consuming from Kafka topic: {}", topic);
        return topic;
    }

    /**
     * Creates the {@link SeparationCalculator} bean used by the detection pipeline.
     *
     * <p>The calculator is a stateless domain utility; a single shared instance suffices.
     */
    @Bean
    public SeparationCalculator separationCalculator() {
        return new SeparationCalculator();
    }

    /**
     * Creates the {@link InfringementDetector} bean used by the detection pipeline.
     *
     * <p>The detector is stateless — it evaluates one radar cycle at a time and returns
     * the infringed pairs for that cycle. It depends on the {@link SeparationCalculator}
     * and {@link SeparationThresholds} beans already wired in this configuration class.
     */
    @Bean
    public InfringementDetector infringementDetector(SeparationCalculator calculator,
                                                     SeparationThresholds thresholds) {
        return new InfringementDetector(calculator, thresholds);
    }

    /**
     * Exposes the observation-window cycle count as a named bean.
     *
     * <p>The observation window is the number of post-event cycles collected after grace
     * period exhaustion before the event is closed and persisted (SAFM-76). If
     * {@code detection.observationWindowCycles} is absent, 12 cycles are used and a WARN
     * is logged. A value below 1 fails startup immediately.
     *
     * @throws IllegalStateException if {@code detection.observationWindowCycles} is set
     *                               to a value less than 1
     */
    @Bean
    public Integer observationWindowCycles(DetectionConfig config) {
        if (config.observationWindowCycles() == null) {
            log.warn("detection.observationWindowCycles not configured -- using default: {} cycles",
                    DEFAULT_OBSERVATION_WINDOW_CYCLES);
            return DEFAULT_OBSERVATION_WINDOW_CYCLES;
        }
        if (config.observationWindowCycles() < 1) {
            throw new IllegalStateException(
                    "Invalid detection.observationWindowCycles: " + config.observationWindowCycles()
                    + " -- must be >= 1.");
        }
        log.info("Observation window configured: {} cycles", config.observationWindowCycles());
        return config.observationWindowCycles();
    }

    /**
     * Exposes the {@link AircraftPositionHistory} bean used to provide pre-event trajectory
     * context when a new infringement event is created (SAFM-76).
     *
     * <p>The history is bounded to {@code observationWindowCycles} entries per callsign
     * (default 12), matching the pre-event context window size.
     */
    @Bean
    public AircraftPositionHistory aircraftPositionHistory(
            @Qualifier("observationWindowCycles") Integer windowCycles) {
        return new AircraftPositionHistory(windowCycles);
    }

    /**
     * Creates the {@link MinSeparationEventLifecycleManager} bean that drives the
     * {@code ACTIVE → GRACE_PERIOD → OBSERVATION_WINDOW → CLOSED} state machine.
     *
     * <p>The registry ({@link InMemoryMinSeparationActiveEventRegistry}) is picked up
     * by component scan — no explicit {@code @Bean} required. The store
     * ({@link com.atcsafety.detection.infrastructure.MinSeparationInfringementStore}) is
     * a {@code @Component} in the infrastructure layer and is injected directly.
     *
     * @param registry                in-memory registry of active events (component-scanned)
     * @param store                   persistence port for closed events
     * @param gracePeriodCycles       number of consecutive re-separation cycles before observation window
     * @param observationWindowCycles number of post-event cycles before closing (SAFM-76)
     */
    @Bean
    public MinSeparationEventLifecycleManager minSeparationEventLifecycleManager(
            MinSeparationActiveEventRegistry registry,
            MinSeparationEventStore store,
            @Qualifier("gracePeriodCycles") Integer gracePeriodCycles,
            @Qualifier("observationWindowCycles") Integer observationWindowCycles,
            SeparationCalculator separationCalculator) {
        return new MinSeparationEventLifecycleManager(
                registry, store, gracePeriodCycles, observationWindowCycles, separationCalculator);
    }
}
