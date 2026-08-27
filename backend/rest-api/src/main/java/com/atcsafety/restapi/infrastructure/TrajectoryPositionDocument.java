package com.atcsafety.restapi.infrastructure;

import java.time.Instant;

/**
 * Embedded MongoDB sub-document representing one aircraft position during an active
 * infringement cycle.
 *
 * <p>Written by the detection service and read here by the REST API. Field names
 * match those serialised by the detection service.
 */
class TrajectoryPositionDocument {

    private String callsign;
    private double x;
    private double y;
    private double altFeet;
    private long radarCycle;
    private Instant cycleTimestamp;

    /**
     * WGS-84 latitude derived from radar coordinates. Null for events persisted
     * before the detection service began storing display coordinates (SAFM-46).
     */
    private Double lat;

    /**
     * WGS-84 longitude derived from radar coordinates. Null for events persisted
     * before the detection service began storing display coordinates (SAFM-46).
     */
    private Double lon;

    /**
     * Ground speed in knots derived from successive radar position updates. Null for events
     * persisted before the detection service began storing speed data (SAFM-62).
     */
    private Double speedKn;

    TrajectoryPositionDocument() {
    }

    String getCallsign() { return callsign; }
    double getX() { return x; }
    double getY() { return y; }
    double getAltFeet() { return altFeet; }
    long getRadarCycle() { return radarCycle; }
    Instant getCycleTimestamp() { return cycleTimestamp; }
    Double getLat() { return lat; }
    Double getLon() { return lon; }
    Double getSpeedKn() { return speedKn; }
}
