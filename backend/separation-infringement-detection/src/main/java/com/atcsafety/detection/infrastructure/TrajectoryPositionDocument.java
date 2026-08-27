package com.atcsafety.detection.infrastructure;

import com.atcsafety.detection.domain.TrajectoryPosition;

import java.time.Instant;

/**
 * Embedded MongoDB document for a single aircraft position snapshot in a trajectory.
 *
 * <p>Mirrors the fields of the domain {@link TrajectoryPosition} value object but
 * lives in the infrastructure layer. This separation ensures domain classes are never
 * used directly as MongoDB documents, preserving the DDD layer boundary.
 *
 * <p>No {@code @Document} annotation — this class is embedded inside
 * {@link MinSeparationInfringementDocument} as a list element.
 *
 * <p>Only {@link MinSeparationInfringementStore} creates instances of this class.
 * Application and domain code work exclusively with {@link TrajectoryPosition}.
 */
class TrajectoryPositionDocument {

    private final String callsign;
    private final double x;
    private final double y;
    private final double altFeet;
    private final double speedKn;
    private final int headingDeg;
    private final Double lat;
    private final Double lon;
    private final long radarCycle;
    private final Instant cycleTimestamp;

    TrajectoryPositionDocument(
            String callsign,
            double x,
            double y,
            double altFeet,
            double speedKn,
            int headingDeg,
            Double lat,
            Double lon,
            long radarCycle,
            Instant cycleTimestamp) {
        this.callsign = callsign;
        this.x = x;
        this.y = y;
        this.altFeet = altFeet;
        this.speedKn = speedKn;
        this.headingDeg = headingDeg;
        this.lat = lat;
        this.lon = lon;
        this.radarCycle = radarCycle;
        this.cycleTimestamp = cycleTimestamp;
    }

    /**
     * Creates a {@link TrajectoryPositionDocument} from the given domain value object.
     *
     * @param pos domain trajectory position
     * @return infrastructure document with the same field values
     */
    static TrajectoryPositionDocument from(TrajectoryPosition pos) {
        return new TrajectoryPositionDocument(
                pos.callsign(),
                pos.x(),
                pos.y(),
                pos.altFeet(),
                pos.speedKn(),
                pos.headingDeg(),
                pos.lat(),
                pos.lon(),
                pos.radarCycle(),
                pos.cycleTimestamp());
    }

    String getCallsign() { return callsign; }
    double getX() { return x; }
    double getY() { return y; }
    double getAltFeet() { return altFeet; }
    double getSpeedKn() { return speedKn; }
    int getHeadingDeg() { return headingDeg; }
    Double getLat() { return lat; }
    Double getLon() { return lon; }
    long getRadarCycle() { return radarCycle; }
    Instant getCycleTimestamp() { return cycleTimestamp; }
}
