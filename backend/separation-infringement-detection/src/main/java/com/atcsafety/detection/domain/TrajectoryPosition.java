package com.atcsafety.detection.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A snapshot of a single aircraft's position in a radar cycle.
 *
 * <p>This is a domain value object — zero Spring, Kafka, or MongoDB imports.
 * It is fully unit-testable with no framework setup.
 *
 * <p>Coordinates ({@code x}, {@code y}) are in metres, using the Cartesian
 * system output by the radar. {@code altFeet} is the Mode C pressure altitude
 * in feet (QNE, standard pressure 1013.25 hPa). Both coordinates and altitude
 * are stored as {@code double} for precision in separation computations.
 *
 * <p>{@code headingDeg} is the aircraft's magnetic heading in degrees (0–359),
 * as reported by the radar. Stored for trajectory replay — not used in
 * separation calculations.
 *
 * <p>{@code lat} and {@code lon} are WGS-84 geographic coordinates derived from
 * the Cartesian position by the Radar Data Processing Service. They are optional
 * (approximately 10 % of radar positions omit them) and are stored solely for
 * map replay.
 *
 * <p>{@code radarCycle} and {@code cycleTimestamp} capture when this snapshot
 * was taken. These are operationally essential: ATC investigators cross-reference
 * trajectory data with Controller Working Position (CWP) display recordings
 * that are indexed by radar cycle number, so non-contiguous gaps in radarCycle
 * values are meaningful and must be preserved.
 *
 * <p><strong>Float-to-double widening:</strong> Radar messages ({@code RadarPositionMessage})
 * use {@code float} fields (matching the Kafka schema). Use the static factory
 * {@link #from(String, float, float, float, float, int, Float, Float, long, Instant)} rather than the record
 * constructor to ensure an explicit, named widening conversion — never rely on
 * implicit widening casts, which can mask precision loss.
 *
 * @param callsign       aircraft identifier (flight number or radar target label)
 * @param x              Cartesian x-coordinate in metres from the radar reference point
 * @param y              Cartesian y-coordinate in metres from the radar reference point
 * @param altFeet        pressure altitude in feet (Mode C, QNE)
 * @param speedKn        ground speed in knots, as derived from successive radar position updates; must not be negative
 * @param headingDeg     magnetic heading in degrees (0–359)
 * @param lat            WGS-84 latitude in decimal degrees; {@code null} when absent
 * @param lon            WGS-84 longitude in decimal degrees; {@code null} when absent
 * @param radarCycle     radar cycle number when this position was observed
 * @param cycleTimestamp UTC timestamp of the radar cycle when this position was observed
 */
public record TrajectoryPosition(
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

    public TrajectoryPosition {
        Objects.requireNonNull(callsign, "callsign must not be null");
        Objects.requireNonNull(cycleTimestamp, "cycleTimestamp must not be null");
        if (callsign.isBlank()) {
            throw new IllegalArgumentException("callsign must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(altFeet)) {
            throw new IllegalArgumentException(
                    "Coordinates and altitude must be finite for callsign: " + callsign);
        }
        if (speedKn < 0) {
            throw new IllegalArgumentException(
                    "speedKn must not be negative for callsign: " + callsign);
        }
    }

    /**
     * Creates a {@link TrajectoryPosition} from the {@code float} fields of a
     * {@code RadarPositionMessage}, performing an explicit widening conversion
     * to {@code double} for each coordinate and speed value.
     *
     * <p>The explicit {@code (double)} cast is intentional: it makes the
     * float-to-double promotion visible in the code rather than relying on
     * Java's implicit widening, which can obscure precision characteristics.
     *
     * <p>{@code lat} and {@code lon} are widened to {@code Double} only when
     * non-null; a {@code null} input maps to a {@code null} output.
     *
     * @param callsign       aircraft identifier
     * @param x              Cartesian x-coordinate in metres (float from radar message)
     * @param y              Cartesian y-coordinate in metres (float from radar message)
     * @param altFeet        pressure altitude in feet (float from radar message)
     * @param speedKn        ground speed in knots (float from radar message); must not be negative
     * @param headingDeg     magnetic heading in degrees (int from radar message)
     * @param lat            WGS-84 latitude; {@code null} when absent in radar message
     * @param lon            WGS-84 longitude; {@code null} when absent in radar message
     * @param radarCycle     radar cycle number when this position was observed
     * @param cycleTimestamp UTC timestamp of the radar cycle
     * @return a new {@link TrajectoryPosition} with double-precision coordinates
     */
    public static TrajectoryPosition from(
            String callsign, float x, float y, float altFeet, float speedKn,
            int headingDeg, Float lat, Float lon,
            long radarCycle, Instant cycleTimestamp) {
        return new TrajectoryPosition(
                callsign,
                (double) x, (double) y, (double) altFeet, (double) speedKn,
                headingDeg,
                lat != null ? (double) lat : null,
                lon != null ? (double) lon : null,
                radarCycle, cycleTimestamp);
    }
}
