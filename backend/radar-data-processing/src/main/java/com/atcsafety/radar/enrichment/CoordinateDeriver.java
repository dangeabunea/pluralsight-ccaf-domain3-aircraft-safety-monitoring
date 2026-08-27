package com.atcsafety.radar.enrichment;

import com.atcsafety.contracts.RadarPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives missing {@code lat}/{@code lon} values from a position's cartesian {@code (x, y)}
 * offsets and the radar installation's fixed geographic coordinates.
 *
 * <p>Uses the equirectangular approximation — accurate within ~200 km of the radar head
 * (POC constraint). Both fields are derived independently: a field that is already present
 * is preserved unchanged.
 *
 * <p>This class is pure domain logic — no Spring, Kafka, or MongoDB imports.
 * SLF4J is the only non-JDK dependency.
 */
public class CoordinateDeriver {

    private static final Logger log = LoggerFactory.getLogger(CoordinateDeriver.class);
    private static final double METRES_PER_DEGREE = 111320.0;

    private final double radarLat;
    private final double radarLon;

    /**
     * Creates a deriver for a radar installation at the given geographic coordinates.
     *
     * @param radarLat radar installation latitude in degrees; must be finite and in [-90, 90]
     * @param radarLon radar installation longitude in degrees; must be finite and in [-180, 180]
     * @throws IllegalArgumentException if either coordinate is out of range or non-finite
     */
    public CoordinateDeriver(double radarLat, double radarLon) {
        if (Double.isNaN(radarLat) || Double.isInfinite(radarLat)
                || radarLat < -90.0 || radarLat > 90.0) {
            throw new IllegalArgumentException(
                    "Invalid radar installation latitude: " + radarLat
                    + " — must be a finite value in [-90, 90]");
        }
        if (Double.isNaN(radarLon) || Double.isInfinite(radarLon)
                || radarLon < -180.0 || radarLon > 180.0) {
            throw new IllegalArgumentException(
                    "Invalid radar installation longitude: " + radarLon
                    + " — must be a finite value in [-180, 180]");
        }
        this.radarLat = radarLat;
        this.radarLon = radarLon;
    }

    /**
     * Returns a {@link RadarPosition} with any absent {@code lat}/{@code lon} derived from
     * the position's cartesian coordinates. Fields that are already present are unchanged.
     *
     * <p>Logs a WARNING for each coordinate that is derived, including the source cartesian
     * value used in the computation.
     *
     * @param position the position to process; must not be null
     * @return a new {@link RadarPosition} with {@code lat} and {@code lon} guaranteed
     *         non-null — derived from cartesian coordinates if absent, or copied unchanged
     *         if already present
     */
    public RadarPosition derive(RadarPosition position) {
        Float lat = position.lat();
        Float lon = position.lon();

        if (lat == null) {
            log.warn("Deriving missing lat for targetId={} from y={}",
                    position.targetId(), position.y());
            lat = (float) (radarLat + (position.y() / METRES_PER_DEGREE));
        }
        if (lon == null) {
            log.warn("Deriving missing lon for targetId={} from x={}",
                    position.targetId(), position.x());
            lon = (float) (radarLon + (position.x()
                    / (METRES_PER_DEGREE * Math.cos(Math.toRadians(radarLat)))));
        }

        return new RadarPosition(
                position.targetId(), position.radarCycle(),
                position.x(), position.y(),
                position.altFeet(), position.speedKn(),
                position.timestampUTC(), position.headingDeg(),
                position.flightNb(), lat, lon
        );
    }
}
