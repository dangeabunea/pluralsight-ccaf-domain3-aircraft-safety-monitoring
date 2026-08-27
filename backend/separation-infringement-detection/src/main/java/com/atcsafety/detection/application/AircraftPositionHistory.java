package com.atcsafety.detection.application;

import com.atcsafety.detection.domain.TrajectoryPosition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rolling per-aircraft position history buffer for pre-event trajectory context.
 *
 * <p>Maintains a bounded deque of the most recent radar positions for each tracked
 * callsign. When the buffer reaches its maximum size, the oldest position is evicted
 * to make room for the new one.
 *
 * <p>Used by {@link CycleDetectionProcessor} to provide pre-event context positions
 * when a new infringement event is created. The buffer is updated for ALL tracked
 * aircraft every cycle — not just infringing pairs.
 *
 * <p>Application-layer class — lives in {@code com.atcsafety.detection.application}.
 * No Spring or Kafka imports; wired as a {@code @Bean} in
 * {@link DetectionApplicationConfig}.
 *
 * <p>Not thread-safe. Single-threaded use is assumed (one Kafka consumer thread).
 */
public class AircraftPositionHistory {

    /**
     * Number of consecutive missed radar cycles before a track is declared lost and
     * its history evicted. Mirrors the ICAO coast threshold — a track is considered
     * lost after 3 consecutive missed updates (~15 seconds at a 5-second scan rate).
     */
    private static final int COAST_THRESHOLD_CYCLES = 3;

    private final int maxHistory;
    private final Map<String, Deque<TrajectoryPosition>> positionsByCallsign;
    private final Map<String, Integer> missedCycleCounters;

    /**
     * Creates a new position history buffer with the specified maximum history size.
     *
     * @param maxHistory maximum number of positions retained per callsign
     */
    public AircraftPositionHistory(int maxHistory) {
        this.maxHistory = maxHistory;
        this.positionsByCallsign = new HashMap<>();
        this.missedCycleCounters = new HashMap<>();
    }

    /**
     * Records a new position for the aircraft identified by {@code position.callsign()}.
     *
     * <p>If the buffer for this callsign has reached {@code maxHistory} entries,
     * the oldest entry is evicted before the new one is added.
     *
     * @param position the latest radar position for the aircraft
     */
    public void update(TrajectoryPosition position) {
        Deque<TrajectoryPosition> deque = positionsByCallsign
                .computeIfAbsent(position.callsign(), cs -> new ArrayDeque<>(maxHistory + 1));
        if (deque.size() == maxHistory) {
            deque.pollFirst(); // evict oldest
        }
        deque.addLast(position);
    }

    /**
     * Returns an immutable snapshot of the position history for the given callsign,
     * in chronological insertion order (oldest first).
     *
     * <p>Returns an empty list if no positions have been recorded for this callsign.
     * The returned list is independent of the buffer — subsequent {@link #update} calls
     * do not affect previously obtained snapshots.
     *
     * @param callsign the aircraft callsign to look up
     * @return unmodifiable ordered list of buffered positions; empty if callsign unknown
     */
    public List<TrajectoryPosition> getSnapshot(String callsign) {
        Deque<TrajectoryPosition> deque = positionsByCallsign.get(callsign);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        return List.copyOf(deque);
    }

    /**
     * Evicts position history for callsigns that have not been seen for
     * {@value #COAST_THRESHOLD_CYCLES} consecutive radar cycles.
     *
     * <p>For each tracked callsign:
     * <ul>
     *   <li>If the callsign is in {@code activeCallsigns}, its missed-cycle counter is reset.</li>
     *   <li>Otherwise, the counter is incremented. When it reaches {@value #COAST_THRESHOLD_CYCLES},
     *       both the position history and the counter are removed — the track is declared lost.</li>
     * </ul>
     *
     * <p>Must be called once per radar cycle, before snapshotting pre-event context, so that
     * stale entries never pollute the context map passed to the lifecycle manager.
     *
     * @param activeCallsigns callsigns that reported a position in the current radar cycle
     */
    public void evictStaleTracks(Set<String> activeCallsigns) {
        // Iterate over a snapshot to avoid ConcurrentModificationException when removing entries
        for (String callsign : new HashSet<>(positionsByCallsign.keySet())) {
            if (activeCallsigns.contains(callsign)) {
                missedCycleCounters.remove(callsign);
            } else {
                int missedCycles = missedCycleCounters.getOrDefault(callsign, 0) + 1;
                if (missedCycles >= COAST_THRESHOLD_CYCLES) {
                    positionsByCallsign.remove(callsign);
                    missedCycleCounters.remove(callsign);
                } else {
                    missedCycleCounters.put(callsign, missedCycles);
                }
            }
        }
    }
}
