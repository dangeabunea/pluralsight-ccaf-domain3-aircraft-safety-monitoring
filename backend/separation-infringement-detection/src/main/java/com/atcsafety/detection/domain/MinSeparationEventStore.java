package com.atcsafety.detection.domain;

/**
 * Port for persisting closed minimum-separation infringement events.
 *
 * <p>Implemented by the infrastructure layer ({@code MinSeparationInfringementStore}).
 * Callers (Story 02-C's {@code EventLifecycleManager}) depend only on this interface —
 * they have no knowledge of MongoDB or Spring Data.
 */
public interface MinSeparationEventStore {

    /**
     * Persists a minimum-separation infringement event.
     *
     * <p>Callers are responsible for only passing {@link MinSeparationEventStatus#CLOSED} events.
     * This contract is enforced by the {@code EventLifecycleManager} (Story 02-C),
     * not by this interface.
     *
     * @param event the infringement event to persist
     */
    void save(MinSeparationInfringementEvent event);
}
