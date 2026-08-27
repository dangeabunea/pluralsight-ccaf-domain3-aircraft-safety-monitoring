package com.atcsafety.radar.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Radar pipeline processing configuration, loaded from {@code application.properties}.
 *
 * <p>Bound to the {@code radar.processing.*} prefix via Spring Boot's configuration-properties
 * mechanism. Override any property with the corresponding environment variable using Spring's
 * relaxed binding (e.g. {@code RADAR_PROCESSING_INTER_CYCLE_DELAY_MS}).
 *
 * <p>Primitive {@code int} is used for {@code interCycleDelayMs} because an absent property
 * and an explicit zero are semantically identical — both mean "no delay." This contrasts with
 * {@link RadarConfig}, which uses boxed {@code Double} because {@code null} (absent) is
 * meaningfully different from {@code 0.0} (equator / prime meridian).
 *
 * <p>Validation of the value (rejecting negative delays) is enforced by the
 * {@code interCycleDelay} bean factory method in {@link RadarPipelineConfig}.
 */
@ConfigurationProperties(prefix = "radar.processing")
public record ProcessingConfig(int interCycleDelayMs) {
}
