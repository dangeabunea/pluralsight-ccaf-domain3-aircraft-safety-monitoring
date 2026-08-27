package com.atcsafety.radar.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Geographic coordinates of the radar installation, loaded from {@code application.properties}.
 *
 * <p>Bound to the {@code radar.*} prefix via Spring Boot's configuration-properties mechanism.
 * Both fields are required: the application will refuse to start if either is absent.
 * The check is enforced in the {@code coordinateDeriver} bean factory method in
 * {@link RadarPipelineConfig}, which produces a descriptive error naming the missing property.
 *
 * <p>Boxed {@code Double} (rather than primitive {@code double}) is used so that absent
 * properties arrive as {@code null} — distinguishable from a legitimately-set zero
 * (equator / prime meridian). A primitive would silently default to {@code 0.0}.
 *
 * <p>This class lives in the {@code application} package rather than {@code domain} because
 * it carries a Spring {@code @ConfigurationProperties} annotation — a framework dependency.
 * The domain layer must remain pure Java with no framework imports.
 */
@ConfigurationProperties(prefix = "radar")
public record RadarConfig(Double lat, Double lon) {
}
