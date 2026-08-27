package com.atcsafety.detection.application;

import com.atcsafety.detection.domain.AircraftPairKey;
import com.atcsafety.detection.domain.MinSeparationActiveEventRegistry;
import com.atcsafety.detection.domain.MinSeparationInfringementEvent;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * In-memory implementation of {@link MinSeparationActiveEventRegistry}.
 *
 * <p>Backed by a {@link HashMap}. Holds all events in {@code ACTIVE} or
 * {@code GRACE_PERIOD} state. Only {@code CLOSED} events are removed — closure
 * hands the aggregate to the {@link com.atcsafety.detection.domain.MinSeparationEventStore}.
 *
 * <p>This registry is intentionally ephemeral: it is not persisted to MongoDB.
 * On service restart all in-progress events are lost — acceptable for the current
 * batch-processing POC pattern.
 *
 * <p>Not thread-safe. Single-threaded use is assumed (one Kafka consumer thread
 * processes radar cycles sequentially).
 */
@Component
public class InMemoryMinSeparationActiveEventRegistry implements MinSeparationActiveEventRegistry {

    private final Map<AircraftPairKey, MinSeparationInfringementEvent> events = new HashMap<>();

    @Override
    public boolean contains(AircraftPairKey key) {
        return events.containsKey(key);
    }

    @Override
    public MinSeparationInfringementEvent get(AircraftPairKey key) {
        return events.get(key);
    }

    @Override
    public void register(AircraftPairKey key, MinSeparationInfringementEvent event) {
        events.put(key, event);
    }

    @Override
    public MinSeparationInfringementEvent remove(AircraftPairKey key) {
        return events.remove(key);
    }

    @Override
    public Collection<AircraftPairKey> activeKeys() {
        return Set.copyOf(events.keySet());
    }
}
