package com.atcsafety.radar.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka producer configuration for the Radar Data Processing Service.
 *
 * <p>Bound to the {@code radar.kafka.*} prefix via Spring Boot's configuration-properties
 * mechanism. The {@code topic} field is required — the application will refuse to start
 * if it is absent. The check is enforced in the {@code kafkaTopic} bean factory method
 * in {@link RadarPipelineConfig}, which produces a descriptive error naming the missing
 * property.
 *
 * <p>This class lives in the {@code application} package because it carries a Spring
 * {@code @ConfigurationProperties} annotation — a framework dependency that disqualifies
 * it from the {@code domain} package (which must remain pure Java with zero framework
 * imports).
 *
 * <p>The named {@code kafkaTopic} {@code String} bean produced by {@link RadarPipelineConfig}
 * is the downstream injection point for {@code RadarPositionPublisher} (SAFM-22/23).
 */
@ConfigurationProperties(prefix = "radar.kafka")
public record KafkaProducerConfig(String topic) {
}
