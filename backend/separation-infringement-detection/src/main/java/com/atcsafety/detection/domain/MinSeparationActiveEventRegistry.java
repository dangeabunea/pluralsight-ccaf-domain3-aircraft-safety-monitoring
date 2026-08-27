package com.atcsafety.detection.domain;

import java.util.Collection;

/**
 * Domain port for the in-memory registry of active minimum-separation infringement events.
 *
 * <p>Holds all events currently in {@code ACTIVE} or {@code GRACE_PERIOD} state.
 * Events are indexed by their canonical {@link AircraftPairKey}. Only
 * {@code CLOSED} events are removed from this registry — closure hands the aggregate
 * to the {@link MinSeparationEventStore} for persistence.
 *
 * <p>This interface lives in the domain layer; its implementation
 * ({@code InMemoryMinSeparationActiveEventRegistry}) lives in the application layer.
 * The lifecycle manager (domain) depends only on this interface and has no knowledge
 * of the backing data structure.
 *
 * <p>Pure Java — zero framework imports. The registry is not thread-safe;
 * single-threaded use per the current batch-processing model is assumed.
 */
public interface MinSeparationActiveEventRegistry {

    /**
     * Returns {@code true} if an event is currently tracked for the given pair.
     *
     * @param key canonical pair key
     * @return {@code true} if the pair has an active event; {@code false} otherwise
     */
    boolean contains(AircraftPairKey key);

    /**
     * Returns the event currently tracked for the given pair, or {@code null} if absent.
     *
     * @param key canonical pair key
     * @return the tracked event, or {@code null} if the pair is not registered
     */
    MinSeparationInfringementEvent get(AircraftPairKey key);

    /**
     * Registers a new event for the given pair.
     *
     * <p>Callers are responsible for ensuring no duplicate event is registered
     * for an already-active pair. The lifecycle manager enforces this invariant.
     *
     * @param key   canonical pair key
     * @param event the new infringement event
     */
    void register(AircraftPairKey key, MinSeparationInfringementEvent event);

    /**
     * Removes and returns the event for the given pair.
     *
     * <p>Called when an event is closed (grace period exhausted). The returned
     * aggregate is handed to the {@link MinSeparationEventStore} for persistence.
     *
     * @param key canonical pair key
     * @return the removed event, or {@code null} if the pair was not registered
     */
    MinSeparationInfringementEvent remove(AircraftPairKey key);

    /**
     * Returns all pair keys currently tracked in the registry.
     *
     * <p>The returned collection is a snapshot — modifications to the registry
     * after this call are not reflected. Callers must not modify the returned collection.
     *
     * @return unmodifiable snapshot of all active pair keys
     */
    Collection<AircraftPairKey> activeKeys();
}
