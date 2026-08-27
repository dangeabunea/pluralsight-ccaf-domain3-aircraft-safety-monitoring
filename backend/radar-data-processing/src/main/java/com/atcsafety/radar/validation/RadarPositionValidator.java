package com.atcsafety.radar.validation;

import com.atcsafety.contracts.RadarPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates radar positions before forwarding to the detection service.
 *
 * <p>Performs two independent checks in order:
 * <ol>
 *   <li><b>Identity (fail-fast)</b> — rejects positions where {@code flightNb} is null
 *       or blank. A position without an identity cannot be tracked by the detection
 *       service and is discarded immediately.
 *   <li><b>Coordinate range</b> — checks {@code lat} ∈ [−90, 90] and
 *       {@code lon} ∈ [−180, 180] when those fields are present. Both fields are
 *       always checked independently so all range violations are reported in a single
 *       call, even when both are out of range. Positions with absent {@code lat}/
 *       {@code lon} pass through unchanged — coordinate derivation is handled upstream.
 * </ol>
 *
 * <p>This class is pure domain logic — no Spring, Kafka, or MongoDB imports.
 */
public class RadarPositionValidator {

    private static final Logger log = LoggerFactory.getLogger(RadarPositionValidator.class);

    /**
     * Validates a radar position's identity and geographic coordinates.
     *
     * @param position the position to validate; must not be null
     * @return {@link ValidationResult.Valid} if the position passes all checks;
     *         {@link ValidationResult.Invalid} with a descriptive reason otherwise
     */
    public ValidationResult validate(RadarPosition position) {
        String flightNb = position.flightNb();
        if (flightNb == null || flightNb.isBlank()) {
            log.warn("Rejecting position targetId={}: flightNb is missing or blank",
                    position.targetId());
            return ValidationResult.Invalid.of("flightNb is missing or blank");
        }

        List<String> failures = new ArrayList<>();

        if (position.lat() != null && (position.lat() < -90.0f || position.lat() > 90.0f)) {
            log.warn("Rejecting position targetId={}: lat={} is outside [-90, 90]",
                    position.targetId(), position.lat());
            failures.add("lat=" + position.lat() + " is outside [-90, 90]");
        }

        if (position.lon() != null && (position.lon() < -180.0f || position.lon() > 180.0f)) {
            log.warn("Rejecting position targetId={}: lon={} is outside [-180, 180]",
                    position.targetId(), position.lon());
            failures.add("lon=" + position.lon() + " is outside [-180, 180]");
        }

        return failures.isEmpty()
                ? ValidationResult.Valid.INSTANCE
                : ValidationResult.Invalid.of(String.join("; ", failures));
    }
}
