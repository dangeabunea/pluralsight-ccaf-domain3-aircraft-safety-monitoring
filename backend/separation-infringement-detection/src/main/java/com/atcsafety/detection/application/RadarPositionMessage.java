package com.atcsafety.detection.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Kafka message payload for the {@code radar.validated-positions} topic.
 *
 * <p>Field names match the Kafka schema defined in SAD §3 exactly (case-sensitive).
 * Jackson serializes record components by their component names — no
 * {@code @JsonProperty} annotations are required since all names are already
 * valid camelCase Java identifiers matching the schema.
 *
 * <p>Per SAD §7 (no shared libraries): this class is defined here in the
 * Separation Infringement Detection Service only. The Radar Data Processing
 * Service defines its own copy. Keeping them separate preserves service autonomy
 * and prevents a shared-library coupling that would force coordinated deployment
 * for every schema change.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures resilience to
 * schema evolution: if the Radar Data Processing Service adds fields in a future
 * release, this consumer ignores them without throwing a deserialization error.
 *
 * <p>This is an application-layer DTO — no domain logic, no framework imports
 * beyond the Jackson annotation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RadarPositionMessage(
        int targetId,
        int radarCycle,
        String flightNb,
        float x,
        float y,
        float altFeet,
        float speedKn,
        int headingDeg,
        Float lat,
        Float lon,
        String timestampUTC) {

    /**
     * The {@code flightNb} value used to identify a flush sentinel message.
     *
     * <p>A flush sentinel is published by the Radar Data Processing Service after
     * the final radar cycle of a dump, signalling the Detection Service to dispatch
     * the last buffered cycle for analysis. Tests must reference this constant rather
     * than the raw string literal so that a future rename propagates automatically.
     */
    public static final String FLUSH_FLIGHT_NB = "__FLUSH__";

    /**
     * Returns {@code true} if this message is the end-of-dump flush sentinel.
     *
     * <p>The flush sentinel has {@code flightNb = "__FLUSH__"} and carries no valid
     * position data. {@link RadarPositionConsumer} checks for the sentinel before
     * any buffer access — the sentinel must never be buffered as a regular position.
     */
    public boolean isFlushSentinel() {
        return FLUSH_FLIGHT_NB.equals(flightNb);
    }
}
