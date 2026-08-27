package com.atcsafety.restapi.application;

import java.time.Instant;

/**
 * One aircraft position recorded during an infringement event lifecycle,
 * including the grace-period cycles that follow the active-infringement phase.
 *
 * <p>{@code lat}, {@code lon}, and {@code speedKn} are nullable for events persisted before the
 * detection service began storing those fields (SAFM-46 for coordinates, SAFM-62 for speed).
 */
public record TrajectoryPositionData(
        long radarCycle,
        Instant timestampUTC,
        double x,
        double y,
        double altFeet,
        Double lat,
        Double lon,
        Double speedKn
) {
}
