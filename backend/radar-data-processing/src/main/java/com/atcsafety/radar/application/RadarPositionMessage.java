package com.atcsafety.radar.application;

/**
 * Kafka message payload for the {@code radar.validated-positions} topic.
 *
 * <p>Field names match the Kafka schema defined in SAD §3 exactly (case-sensitive).
 * Jackson serializes record components by their component names — no
 * {@code @JsonProperty} annotations are required since all names are already
 * valid camelCase Java identifiers matching the schema.
 *
 * <p>Absent coordinates ({@code lat}, {@code lon}) are serialized as JSON
 * {@code null} (Jackson default for boxed types).
 *
 * <p>Per SAD §7 (no shared libraries): this class is defined here in the
 * Radar Data Processing Service only. The Detection Service defines its own
 * copy (Task 02-F-1).
 *
 * <p>This is an application-layer DTO — no domain logic, no framework imports.
 */
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
        String timestampUTC
) {}
