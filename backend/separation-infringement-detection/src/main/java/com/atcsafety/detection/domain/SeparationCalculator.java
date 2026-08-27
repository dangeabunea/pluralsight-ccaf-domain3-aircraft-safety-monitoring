package com.atcsafety.detection.domain;

/**
 * Computes horizontal and vertical separation between two aircraft positions.
 *
 * <p>This is a pure domain class — zero Spring, Kafka, or MongoDB imports.
 * It is fully unit-testable with no framework setup. Callers instantiate it
 * directly ({@code new SeparationCalculator()}) — it is not a Spring bean.
 *
 * <p><strong>Horizontal separation:</strong> Euclidean distance on the Cartesian
 * (x, y) plane in metres, divided by 1852 to yield nautical miles. This is an
 * appropriate approximation for a single-radar system covering a finite en-route
 * sector (confirmed by ATC SME: Euclidean distance on projected Cartesian
 * coordinates is operationally standard for single-radar separation assessment).
 *
 * <p><strong>Vertical separation:</strong> Absolute difference of Mode C pressure
 * altitude values in feet. Both aircraft are assumed to be on the same pressure
 * reference (QNE). See {@link TrajectoryPosition} for altimetry assumptions.
 */
public class SeparationCalculator {

    /**
     * Standard conversion factor: 1 nautical mile = 1852 metres exactly,
     * per ICAO Annex 5 and ISO 80000-3.
     */
    private static final double METRES_PER_NAUTICAL_MILE = 1852.0;

    /**
     * Computes the horizontal separation between two aircraft in nautical miles.
     *
     * <p>Uses Euclidean distance on the Cartesian (x, y) coordinate plane.
     * Input coordinates must be in metres.
     *
     * @param a first aircraft position
     * @param b second aircraft position
     * @return horizontal separation in nautical miles; always &gt;= 0.0
     */
    public double horizontalSeparationNm(TrajectoryPosition a, TrajectoryPosition b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double distanceMetres = Math.sqrt(dx * dx + dy * dy);
        return distanceMetres / METRES_PER_NAUTICAL_MILE;
    }

    /**
     * Computes the vertical separation between two aircraft in feet.
     *
     * <p>Returns the absolute difference of their Mode C pressure altitudes.
     * The result is always &gt;= 0.0.
     *
     * @param a first aircraft position
     * @param b second aircraft position
     * @return vertical separation in feet; always &gt;= 0.0
     */
    public double verticalSeparationFt(TrajectoryPosition a, TrajectoryPosition b) {
        return Math.abs(a.altFeet() - b.altFeet());
    }
}
