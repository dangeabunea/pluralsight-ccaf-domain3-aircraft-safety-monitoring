package com.atcsafety.radar.application;

import com.atcsafety.contracts.RadarPosition;
import com.atcsafety.radar.enrichment.CoordinateDeriver;
import com.atcsafety.radar.loading.RadarDumpLoader;
import com.atcsafety.radar.validation.RadarPositionValidator;
import com.atcsafety.radar.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the radar data processing pipeline at application startup.
 *
 * <p>Execution order per position:
 * <ol>
 *   <li>Load all positions from the radar dump file ({@link RadarDumpLoader}).
 *   <li>Derive any missing {@code lat}/{@code lon} from cartesian coordinates
 *       ({@link CoordinateDeriver}).
 *   <li>Validate the enriched position ({@link RadarPositionValidator}).
 *   <li>If valid: publish the position as a Kafka message to the configured topic
 *       ({@link RadarPositionPublisher}).
 *   <li>If invalid: no further action — the validator has already logged a WARNING.
 * </ol>
 *
 * <p>After all positions are processed, a flush sentinel is published via
 * {@link RadarPositionPublisher#publishFlush(int)} so that the Detection Service can
 * trigger processing of the final buffered cycle without end-of-topic detection logic
 * (SAD §3). The flush is sent even when no valid positions were published.
 *
 * <p>This class is in the application layer — Spring imports are permitted here.
 */
@Service
public class RadarProcessingPipeline implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RadarProcessingPipeline.class);

    private final RadarDumpLoader radarDumpLoader;
    private final CoordinateDeriver coordinateDeriver;
    private final RadarPositionValidator validator;
    private final RadarPositionPublisher publisher;
    private final InterCycleDelay interCycleDelay;

    public RadarProcessingPipeline(
            RadarDumpLoader radarDumpLoader,
            CoordinateDeriver coordinateDeriver,
            RadarPositionValidator validator,
            RadarPositionPublisher publisher,
            InterCycleDelay interCycleDelay) {
        this.radarDumpLoader = radarDumpLoader;
        this.coordinateDeriver = coordinateDeriver;
        this.validator = validator;
        this.publisher = publisher;
        this.interCycleDelay = interCycleDelay;
    }

    /**
     * Runs the pipeline over all positions in the radar dump file.
     *
     * <p>Each position is processed independently. A {@link RuntimeException} thrown
     * during enrichment, validation, or publishing is caught, logged at ERROR level,
     * and does not halt processing of the remaining positions.
     *
     * @param args application startup arguments (unused)
     */
    @Override
    public void run(ApplicationArguments args) {
        List<RadarPosition> positions = radarDumpLoader.load();
        log.info("Loaded {} radar positions from dump file", positions.size());

        int publishedCount = 0;
        int rejectedCount = 0;
        int errorCount = 0;
        int lastPublishedCycle = 0;
        int cycleCount = 0;

        int currentCycle = -1;
        int cyclePublished = 0;
        int cycleRejected = 0;
        int cycleErrors = 0;

        for (RadarPosition raw : positions) {
            if (raw.radarCycle() != currentCycle) {
                if (currentCycle != -1) {
                    log.info("Cycle {} — published={}, rejected={}, errors={}",
                            currentCycle, cyclePublished, cycleRejected, cycleErrors);
                    cycleCount++;
                    interCycleDelay.delay();
                }
                currentCycle = raw.radarCycle();
                cyclePublished = 0;
                cycleRejected = 0;
                cycleErrors = 0;
            }

            try {
                RadarPosition enriched = coordinateDeriver.derive(raw);

                switch (validator.validate(enriched)) {
                    case ValidationResult.Valid v -> {
                        publisher.publish(enriched);
                        lastPublishedCycle = enriched.radarCycle();
                        publishedCount++;
                        cyclePublished++;
                    }
                    case ValidationResult.Invalid i -> {
                        rejectedCount++;
                        cycleRejected++;
                    }
                }
            } catch (RuntimeException e) {
                log.error("Exception processing position targetId={} — {}: {}",
                        raw.targetId(), e.getClass().getName(), e.getMessage());
                errorCount++;
                cycleErrors++;
            }
        }

        if (currentCycle != -1) {
            log.info("Cycle {} — published={}, rejected={}, errors={}",
                    currentCycle, cyclePublished, cycleRejected, cycleErrors);
            cycleCount++;
        }

        publisher.publishFlush(lastPublishedCycle);

        log.info("Pipeline complete — {} cycles processed, published={}, rejected={}, errors={}",
                cycleCount, publishedCount, rejectedCount, errorCount);
    }
}
