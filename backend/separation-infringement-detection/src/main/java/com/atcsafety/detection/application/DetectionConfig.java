package com.atcsafety.detection.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Separation Infringement Detection Service.
 *
 * <p>Bound to the {@code detection.*} prefix via Spring Boot's
 * configuration-properties mechanism.
 *
 * <p>Optional fields ({@code horizontalThresholdNm}, {@code verticalThresholdFt},
 * {@code gracePeriodCycles}) use boxed types so that an absent property arrives
 * as {@code null} — distinguishable from a deliberately-set zero value.
 * A primitive would silently default to {@code 0}, masking the absence.
 * Defaults are applied in {@link DetectionApplicationConfig} with a WARN log.
 *
 * <p>The {@code kafka} nested record carries the Kafka topic name for the
 * {@code radar.validated-positions} consumer. It is required — startup fails
 * with a descriptive error if it is absent or blank.
 *
 * <p>This record lives in the {@code application} package because it carries
 * a Spring {@code @ConfigurationProperties} annotation — a framework dependency
 * that disqualifies it from the {@code domain} package (which must remain pure
 * Java with zero framework imports).
 *
 * @param horizontalThresholdNm   horizontal separation threshold in NM; null if not set
 * @param verticalThresholdFt     vertical separation threshold in feet; null if not set
 * @param gracePeriodCycles       grace-period cycle count before entering observation window; null if not set
 * @param observationWindowCycles post-event observation window cycle count (SAFM-76); null if not set
 * @param kafka                   nested Kafka consumer configuration
 */
@ConfigurationProperties(prefix = "detection")
public record DetectionConfig(
        Double horizontalThresholdNm,
        Integer verticalThresholdFt,
        Integer gracePeriodCycles,
        Integer observationWindowCycles,
        Kafka kafka) {

    /**
     * Nested Kafka consumer configuration bound to the {@code detection.kafka.*} prefix.
     *
     * @param topic  the Kafka topic to consume from; required — must not be blank
     */
    public record Kafka(String topic) {
    }
}
