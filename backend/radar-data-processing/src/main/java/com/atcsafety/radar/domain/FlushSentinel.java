package com.atcsafety.radar.domain;

/**
 * Domain constant identifying the flush sentinel message published at the end of every
 * radar dump run.
 *
 * <p>The sentinel signals the Detection Service to flush and process the final buffered
 * cycle without requiring end-of-topic detection logic (SAD §3). Its {@code flightNb}
 * field carries this constant, which is also used as the Kafka message key.
 *
 * <p>This class lives in {@code domain/} because the sentinel identity is a domain
 * concept — it is referenced by both the publishing side (Radar Data Processing Service)
 * and the consuming side (Detection Service). Placing it here keeps the constant
 * authoritative and free of framework imports.
 */
public final class FlushSentinel {

    public static final String FLIGHT_NB = "__FLUSH__";

    private FlushSentinel() {
    }
}
