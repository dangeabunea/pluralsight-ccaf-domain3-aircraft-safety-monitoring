package com.atcsafety.detection.domain;

/**
 * Canonical, order-independent key for an unordered aircraft pair.
 *
 * <p>Two aircraft that are simultaneously in separation infringement are always
 * referred to as a pair, regardless of which callsign was checked first.
 * This record enforces a canonical ordering — callsigns are stored
 * lexicographically ascending — so that {@code AircraftPairKey.of("BA123", "AF456")}
 * and {@code AircraftPairKey.of("AF456", "BA123")} produce an equal key.
 *
 * <p>Record {@code equals()} and {@code hashCode()} are derived from the two
 * components, which are always in canonical order after construction via
 * {@link #of(String, String)}. This makes the key safe to use in {@link java.util.Set}
 * and {@link java.util.Map} without additional comparators.
 *
 * <p>Pure Java — zero framework imports.
 *
 * @param callsign1 lexicographically smaller callsign of the pair
 * @param callsign2 lexicographically larger callsign of the pair
 */
public record AircraftPairKey(String callsign1, String callsign2) {

    /**
     * Creates an {@link AircraftPairKey} for the given two callsigns, normalising
     * their order so that {@code callsign1} is always lexicographically smaller or equal.
     *
     * @param a first callsign (order does not matter)
     * @param b second callsign (order does not matter)
     * @return canonical pair key with components in lexicographic order
     */
    public static AircraftPairKey of(String a, String b) {
        return a.compareTo(b) <= 0
                ? new AircraftPairKey(a, b)
                : new AircraftPairKey(b, a);
    }

    /**
     * Returns the pair as a human-readable string of the form {@code "callsign1-callsign2"}.
     *
     * <p>Because the components are always in canonical lexicographic order, this string
     * is stable regardless of the order in which the two callsigns were supplied to
     * {@link #of(String, String)}.
     *
     * @return e.g. {@code "AF456-BA123"}
     */
    public String asString() {
        return callsign1 + "-" + callsign2;
    }
}
