package com.atcsafety.detection.domain;

/**
 * Lifecycle states of a separation infringement event.
 *
 * <p>State machine: {@code ACTIVE → GRACE_PERIOD → OBSERVATION_WINDOW → CLOSED}.
 * An event re-enters {@code ACTIVE} if the aircraft pair breaches thresholds
 * again during the grace period. Only {@code CLOSED} events are persisted to MongoDB.
 *
 * <p>{@code OBSERVATION_WINDOW} is entered after grace period exhaustion (Story 07-O,
 * SAFM-76). The event continues collecting post-event position data for 12 additional
 * radar cycles before being closed and persisted. If the pair re-infringes during the
 * observation window, the event is discarded and a fresh event is created.
 *
 * <p>Lifecycle transition logic is implemented in Story 02-C (EventLifecycleManager).
 * This enum exists here as a data stub so the persistence layer (SAFM-34) can store
 * status without depending on lifecycle code that does not yet exist.
 */
public enum MinSeparationEventStatus {
    ACTIVE,
    GRACE_PERIOD,
    OBSERVATION_WINDOW,
    CLOSED
}
