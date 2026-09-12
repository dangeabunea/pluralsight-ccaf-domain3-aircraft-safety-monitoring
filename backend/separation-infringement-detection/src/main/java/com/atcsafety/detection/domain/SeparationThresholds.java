package com.atcsafety.detection.domain;

/**
 * Validated separation thresholds used by all detection logic in this service.
 *
 * <p>An infringement is triggered when two aircraft simultaneously breach
 * both thresholds: horizontal separation below {@code horizontalThresholdNm}
 * nautical miles AND vertical separation below {@code verticalThresholdFt} feet.
 *
 * <p>Threshold values are sourced from {@code application.properties} and validated
 * at startup by {@code DetectionApplicationConfig}. This record receives only
 * pre-validated values — it performs no validation itself.
 *
 * <p>Default values when properties are absent:
 * <ul>
 *   <li>{@code horizontalThresholdNm} = 5.0 NM</li>
 *   <li>{@code verticalThresholdFt} = 1000 ft</li>
 * </ul>
 *
 * <p>This record is pure Java with zero framework imports, keeping the domain layer
 * unit-testable without any Spring context setup.
 *
 * @param horizontalThresholdNm  horizontal separation threshold in nautical miles
 * @param verticalThresholdFt    vertical separation threshold in feet
 */
public record SeparationThresholds(double horizontalThresholdNm, int verticalThresholdFt) {
}
