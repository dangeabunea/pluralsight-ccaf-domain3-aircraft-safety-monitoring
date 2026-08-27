package com.atcsafety.detection.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Separation infringement event — the core aggregate of this service.
 *
 * <p>Captures a period during which two aircraft simultaneously breached both the
 * horizontal (5 NM) and vertical (1000 ft) separation thresholds.
 *
 * <p>This class implements the
 * {@code ACTIVE → GRACE_PERIOD → OBSERVATION_WINDOW → CLOSED} state machine.
 * Only {@code CLOSED} events are persisted to MongoDB — closure removes the aggregate
 * from the in-memory registry entirely.
 *
 * <p>Pure Java — zero framework imports. Fully unit-testable with no Spring context.
 *
 * <h2>Lifecycle (SAFM-37 + SAFM-76)</h2>
 * <ol>
 *   <li>Created via {@link #createNew} — status is {@code ACTIVE}. Optional pre-event
 *       positions are prepended to the trajectory arrays.</li>
 *   <li>{@link #extendActive} — called while pair continues to infringe.</li>
 *   <li>{@link #enterGracePeriod} — called when pair re-separates; counter set to
 *       {@code gracePeriodCycles}.</li>
 *   <li>{@link #decrementGracePeriod} — called once per subsequent cycle without
 *       re-infringement.</li>
 *   <li>{@link #isGracePeriodExhausted} — returns {@code true} when counter reaches 0;
 *       caller calls {@link #enterObservationWindow()}.</li>
 *   <li>{@link #reenterActive} — called if the pair re-infringes during the grace period.</li>
 *   <li>{@link #enterObservationWindow} — transitions to {@code OBSERVATION_WINDOW};
 *       stores {@code eventEndCycle}; initialises the post-event position counter.</li>
 *   <li>{@link #appendPostEventPosition} — adds one post-event cycle position pair;
 *       increments {@code observationPositionCount}.</li>
 *   <li>{@link #isObservationWindowComplete} — returns {@code true} when
 *       {@code observationPositionCount} ≥ the requested window size.</li>
 *   <li>{@link #close} — transitions to {@code CLOSED}; caller persists and de-registers.</li>
 * </ol>
 *
 * <h2>Trajectory (SAFM-76)</h2>
 * <p>The trajectory arrays contain three segments in order:
 * <ol>
 *   <li><b>Pre-event context</b> — up to 12 cycles before the first infringement,
 *       prepended at creation time from the {@code AircraftPositionHistory} buffer.</li>
 *   <li><b>Infringement cycles</b> — ACTIVE cycles only (no grace-period positions).</li>
 *   <li><b>Post-event context</b> — up to 12 cycles appended via
 *       {@link #appendPostEventPosition} during the observation window.</li>
 * </ol>
 *
 * <h2>eventStartCycle / eventEndCycle (SAFM-76)</h2>
 * <p>{@code eventStartCycle} is the 0-based trajectory index of the first infringing cycle
 * (= pre-event count, max 12). {@code eventEndCycle} is the 0-based index of the last
 * infringing cycle; it is set at {@link #enterObservationWindow()} time (sentinel value
 * {@code -1} until that point).
 *
 * <h2>endTime</h2>
 * <p>{@code lastActiveCycleTimestamp} is updated on every ACTIVE entry and re-entry.
 * It is <em>not</em> updated during grace-period or observation-window cycles. At closure,
 * callers use {@link #getLastActiveCycleTimestamp()} as the event {@code endTime}.
 *
 * <h2>Minimum separation</h2>
 * <p>The running minimum horizontal separation (and the corresponding vertical
 * separation and trajectory index) is tracked across the full ACTIVE lifetime of the
 * event, including re-entries. The index is relative to the full trajectory array —
 * i.e. it is offset by the pre-event position count.
 */
// 26 methods: 9 lifecycle mutations + 17 state accessors.
// Splitting the class would break cohesion of the state machine aggregate.
@SuppressWarnings("PMD.TooManyMethods")
public class MinSeparationInfringementEvent {

    // -- Final identity fields --
    private final String eventId;
    private final String callsign1;
    private final String callsign2;
    private final long startCycle;
    private final Instant startTime;
    private final int gracePeriodCycles;

    // -- Mutable lifecycle fields --
    private MinSeparationEventStatus status;
    private int gracePeriodCounter;
    private Instant lastActiveCycleTimestamp;

    // -- Trajectory (pre-event + ACTIVE + post-event) --
    private final List<TrajectoryPosition> trajectory1;
    private final List<TrajectoryPosition> trajectory2;

    // -- Running minimum separation (SAFM-46 / SAFM-64) --
    private double minHorizontalSeparationNm;
    private double minVerticalSeparationFt;
    private int minSeparationCycleIndex;
    private int minVerticalSeparationCycleIndex;

    // -- SAFM-76: trajectory index metadata --
    /** 0-based trajectory index of the first infringing cycle (= pre-event count). */
    private final int eventStartCycle;

    /**
     * 0-based trajectory index of the last infringing cycle.
     * Set on {@link #enterObservationWindow()}. Sentinel value {@code -1} before that.
     */
    private int eventEndCycle;

    /** Number of post-event positions appended during the observation window. */
    private int observationPositionCount;

    private MinSeparationInfringementEvent(
            String eventId,
            String callsign1,
            String callsign2,
            long startCycle,
            Instant startTime,
            int gracePeriodCycles,
            MinSeparationEventStatus status,
            int gracePeriodCounter,
            Instant lastActiveCycleTimestamp,
            List<TrajectoryPosition> trajectory1,
            List<TrajectoryPosition> trajectory2,
            double minHorizontalSeparationNm,
            double minVerticalSeparationFt,
            int minSeparationCycleIndex,
            int minVerticalSeparationCycleIndex,
            int eventStartCycle) {
        this.eventId = eventId;
        this.callsign1 = callsign1;
        this.callsign2 = callsign2;
        this.startCycle = startCycle;
        this.startTime = startTime;
        this.gracePeriodCycles = gracePeriodCycles;
        this.status = status;
        this.gracePeriodCounter = gracePeriodCounter;
        this.lastActiveCycleTimestamp = lastActiveCycleTimestamp;
        this.trajectory1 = trajectory1;
        this.trajectory2 = trajectory2;
        this.minHorizontalSeparationNm = minHorizontalSeparationNm;
        this.minVerticalSeparationFt = minVerticalSeparationFt;
        this.minSeparationCycleIndex = minSeparationCycleIndex;
        this.minVerticalSeparationCycleIndex = minVerticalSeparationCycleIndex;
        this.eventStartCycle = eventStartCycle;
        this.eventEndCycle = -1; // sentinel until enterObservationWindow()
        this.observationPositionCount = 0;
    }

    /**
     * Creates a new separation infringement event, optionally prepending pre-event
     * context positions from the rolling history buffer.
     *
     * <p>Pre-event positions are prepended to {@code trajectory1}/{@code trajectory2}
     * before the first infringing position ({@code positionA}/{@code positionB}).
     * If both pre-event lists are empty, the trajectory contains only the first
     * infringing position — equivalent to legacy behaviour.
     *
     * <p>{@code eventStartCycle} = {@code preEventPositions1.size()} — the 0-based
     * index of the first infringing cycle in the trajectory array.
     *
     * <p>{@code minSeparationCycleIndex} is initialised to {@code eventStartCycle}
     * because the first infringing cycle is the closest approach observed so far.
     *
     * @param pair                    canonical pair key (lexicographically ordered callsigns)
     * @param startCycle              radar cycle number when the infringement was first detected
     * @param startTime               UTC timestamp of the start cycle
     * @param gracePeriodCycles       number of consecutive re-separation cycles before closure
     * @param preEventPositions1      pre-event positions for aircraft 1 (may be empty)
     * @param preEventPositions2      pre-event positions for aircraft 2 (may be empty)
     * @param positionA               first infringing position of aircraft 1
     * @param positionB               first infringing position of aircraft 2
     * @param horizontalSeparationNm  computed horizontal separation in NM at the start cycle
     * @param verticalSeparationFt    computed vertical separation in feet at the start cycle
     * @return a new event in {@code ACTIVE} state
     */
    public static MinSeparationInfringementEvent createNew(
            AircraftPairKey pair,
            long startCycle,
            Instant startTime,
            int gracePeriodCycles,
            List<TrajectoryPosition> preEventPositions1,
            List<TrajectoryPosition> preEventPositions2,
            TrajectoryPosition positionA,
            TrajectoryPosition positionB,
            double horizontalSeparationNm,
            double verticalSeparationFt) {

        if (gracePeriodCycles <= 0) {
            throw new IllegalArgumentException(
                    "gracePeriodCycles must be positive, got: " + gracePeriodCycles);
        }

        String eventId = pair.callsign1() + "-" + pair.callsign2() + "-" + startCycle;
        int preEventCount = preEventPositions1.size();

        List<TrajectoryPosition> traj1 = new ArrayList<>(preEventCount + 1);
        traj1.addAll(preEventPositions1);
        traj1.add(positionA);

        List<TrajectoryPosition> traj2 = new ArrayList<>(preEventCount + 1);
        traj2.addAll(preEventPositions2);
        traj2.add(positionB);

        return new MinSeparationInfringementEvent(
                eventId,
                pair.callsign1(),
                pair.callsign2(),
                startCycle,
                startTime,
                gracePeriodCycles,
                MinSeparationEventStatus.ACTIVE,
                0,
                startTime,
                traj1,
                traj2,
                horizontalSeparationNm,
                verticalSeparationFt,
                preEventCount, // minSeparationCycleIndex starts at first infringing position
                preEventCount, // minVerticalSeparationCycleIndex starts at first infringing position
                preEventCount  // eventStartCycle = pre-event count
        );
    }

    /**
     * Extends this event for one more cycle while the pair remains in infringement.
     *
     * <p>Appends a trajectory entry for each aircraft and updates
     * {@code lastActiveCycleTimestamp} to the given cycle timestamp.
     * If the supplied horizontal separation is strictly less than the current minimum,
     * all three minimum-separation fields are updated.
     * Status remains {@code ACTIVE}.
     *
     * @param cycleTimestamp         UTC timestamp of the current cycle
     * @param positionA              current position of aircraft 1
     * @param positionB              current position of aircraft 2
     * @param horizontalSeparationNm computed horizontal separation in NM for this cycle
     * @param verticalSeparationFt   computed vertical separation in feet for this cycle
     */
    public void extendActive(Instant cycleTimestamp, TrajectoryPosition positionA, TrajectoryPosition positionB,
                             double horizontalSeparationNm, double verticalSeparationFt) {
        trajectory1.add(positionA);
        trajectory2.add(positionB);
        lastActiveCycleTimestamp = cycleTimestamp;
        if (horizontalSeparationNm < minHorizontalSeparationNm) {
            minHorizontalSeparationNm = horizontalSeparationNm;
            minSeparationCycleIndex = trajectory1.size() - 1;
        }
        if (verticalSeparationFt < minVerticalSeparationFt) {
            minVerticalSeparationFt = verticalSeparationFt;
            minVerticalSeparationCycleIndex = trajectory1.size() - 1;
        }
    }

    /**
     * Transitions this event to {@code GRACE_PERIOD} when the pair re-separates.
     *
     * <p>Sets the grace-period counter to {@code gracePeriodCycles}. No trajectory
     * entry is appended — grace-period cycles are not part of the investigation record.
     * {@code lastActiveCycleTimestamp} is not updated.
     */
    public void enterGracePeriod() {
        status = MinSeparationEventStatus.GRACE_PERIOD;
        gracePeriodCounter = gracePeriodCycles;
    }

    /**
     * Decrements the grace-period counter by one cycle.
     *
     * <p>Called once per radar cycle in which the pair remains separated.
     * When the counter reaches 0, the caller should check {@link #isGracePeriodExhausted()}
     * and call {@link #enterObservationWindow()}.
     */
    public void decrementGracePeriod() {
        if (gracePeriodCounter > 0) gracePeriodCounter--;
    }

    /**
     * Returns {@code true} when the grace-period counter has reached 0,
     * indicating the observation window should be entered.
     *
     * @return {@code true} if the grace period is exhausted; {@code false} otherwise
     */
    public boolean isGracePeriodExhausted() {
        return gracePeriodCounter == 0;
    }

    /**
     * Transitions this event to {@code OBSERVATION_WINDOW} after grace period exhaustion.
     *
     * <p>Records {@code eventEndCycle} as {@code trajectory1.size() - 1} — the 0-based
     * index of the last infringing cycle (= eventStartCycle + infringement_cycles - 1).
     * Initialises {@code observationPositionCount} to {@code 0}.
     *
     * <p>After this call, the lifecycle manager appends post-event positions via
     * {@link #appendPostEventPosition} until {@link #isObservationWindowComplete} is true,
     * then calls {@link #close()} and persists the event.
     */
    public void enterObservationWindow() {
        status = MinSeparationEventStatus.OBSERVATION_WINDOW;
        eventEndCycle = trajectory1.size() - 1;
        observationPositionCount = 0;
    }

    /**
     * Appends one post-event position pair to the trajectory during the observation window.
     *
     * <p>Called by the lifecycle manager once per radar cycle while the event is in
     * {@code OBSERVATION_WINDOW} state. Does not update {@code lastActiveCycleTimestamp}
     * or any minimum-separation fields.
     *
     * @param positionA post-event position of aircraft 1
     * @param positionB post-event position of aircraft 2
     */
    public void appendPostEventPosition(TrajectoryPosition positionA, TrajectoryPosition positionB) {
        trajectory1.add(positionA);
        trajectory2.add(positionB);
        observationPositionCount++;
    }

    /**
     * Returns {@code true} when the observation window is complete — i.e. the number of
     * post-event positions appended has reached or exceeded the requested window size.
     *
     * @param windowSize the target number of post-event cycles (e.g. 12)
     * @return {@code true} if the window is complete; {@code false} otherwise
     */
    public boolean isObservationWindowComplete(int windowSize) {
        return observationPositionCount >= windowSize;
    }

    /**
     * Transitions this event to {@code CLOSED}.
     *
     * <p>Called by the lifecycle manager immediately before removing the event from
     * the registry and passing it to the persistence store. After this call, the event
     * must not be mutated further.
     */
    public void close() {
        status = MinSeparationEventStatus.CLOSED;
    }

    /**
     * Re-enters {@code ACTIVE} state when the pair breaches thresholds again during
     * the grace period.
     *
     * <p>Resets the grace-period counter to {@code gracePeriodCycles}, appends
     * trajectory entries, and updates {@code lastActiveCycleTimestamp}.
     * If the supplied horizontal separation is strictly less than the current minimum,
     * all three minimum-separation fields are updated.
     *
     * @param cycleTimestamp         UTC timestamp of the current cycle
     * @param positionA              current position of aircraft 1
     * @param positionB              current position of aircraft 2
     * @param horizontalSeparationNm computed horizontal separation in NM for this cycle
     * @param verticalSeparationFt   computed vertical separation in feet for this cycle
     */
    public void reenterActive(Instant cycleTimestamp, TrajectoryPosition positionA, TrajectoryPosition positionB,
                              double horizontalSeparationNm, double verticalSeparationFt) {
        status = MinSeparationEventStatus.ACTIVE;
        gracePeriodCounter = gracePeriodCycles;
        trajectory1.add(positionA);
        trajectory2.add(positionB);
        lastActiveCycleTimestamp = cycleTimestamp;
        if (horizontalSeparationNm < minHorizontalSeparationNm) {
            minHorizontalSeparationNm = horizontalSeparationNm;
            minSeparationCycleIndex = trajectory1.size() - 1;
        }
        if (verticalSeparationFt < minVerticalSeparationFt) {
            minVerticalSeparationFt = verticalSeparationFt;
            minVerticalSeparationCycleIndex = trajectory1.size() - 1;
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** Unique event identifier: {@code "<callsign1>-<callsign2>-<startCycle>"}. */
    public String getEventId() { return eventId; }

    /** Lexicographically first callsign in the pair. */
    public String getCallsign1() { return callsign1; }

    /** Lexicographically second callsign in the pair. */
    public String getCallsign2() { return callsign2; }

    /** Radar cycle number when the infringement was first detected. */
    public long getStartCycle() { return startCycle; }

    /** UTC timestamp of the radar cycle when the infringement was first detected. */
    public Instant getStartTime() { return startTime; }

    /** Number of consecutive re-separation cycles before the event closes. */
    public int getGracePeriodCycles() { return gracePeriodCycles; }

    /** Current lifecycle state of the event. */
    public MinSeparationEventStatus getStatus() { return status; }

    /** Current value of the grace-period countdown counter. */
    int getGracePeriodCounter() { return gracePeriodCounter; }

    /**
     * Timestamp of the last cycle where both thresholds were simultaneously breached.
     *
     * <p>Updated on every ACTIVE entry and re-entry. This is used as {@code endTime}
     * when the event is closed and persisted.
     */
    public Instant getLastActiveCycleTimestamp() { return lastActiveCycleTimestamp; }

    /**
     * Unmodifiable view of the full trajectory for aircraft 1: pre-event context +
     * ACTIVE infringement cycles + post-event context (when observation window active).
     */
    public List<TrajectoryPosition> getTrajectory1() {
        return Collections.unmodifiableList(trajectory1);
    }

    /**
     * Unmodifiable view of the full trajectory for aircraft 2. Index-aligned with
     * {@link #getTrajectory1()}.
     */
    public List<TrajectoryPosition> getTrajectory2() {
        return Collections.unmodifiableList(trajectory2);
    }

    /**
     * Minimum horizontal separation observed across all ACTIVE infringement cycles,
     * including re-entries. Updated whenever a new closest approach is detected.
     *
     * @return horizontal separation in nautical miles at the point of closest approach
     */
    public double getMinHorizontalSeparationNm() { return minHorizontalSeparationNm; }

    /**
     * True minimum vertical separation observed across all ACTIVE infringement cycles,
     * including re-entries. Tracked independently of the horizontal minimum — the two
     * minima may occur at different trajectory indices (SAFM-64).
     *
     * @return vertical separation in feet at the cycle of closest vertical approach
     */
    public double getMinVerticalSeparationFt() { return minVerticalSeparationFt; }

    /**
     * 0-based index into {@link #getTrajectory1()} / {@link #getTrajectory2()} pointing
     * to the cycle where the minimum vertical separation was observed.
     *
     * <p>Tracked independently of {@link #getMinSeparationCycleIndex()} — the two indices
     * will differ whenever the closest horizontal and closest vertical approaches occur on
     * different cycles (SAFM-64).
     *
     * @return trajectory index of the closest-vertical-approach cycle
     */
    public int getMinVerticalSeparationCycleIndex() { return minVerticalSeparationCycleIndex; }

    /**
     * 0-based index into {@link #getTrajectory1()} / {@link #getTrajectory2()} pointing
     * to the cycle where the minimum horizontal separation was observed.
     *
     * <p>Offset by the pre-event position count — if 12 pre-event positions were prepended,
     * the first infringing cycle is at index 12 and this field is initialized to 12.
     *
     * @return trajectory index of the closest-approach cycle
     */
    public int getMinSeparationCycleIndex() { return minSeparationCycleIndex; }

    /**
     * 0-based trajectory index of the first infringing cycle.
     *
     * <p>Equal to the number of pre-event positions prepended at creation time (max 12,
     * or less on cold-start). Zero if no pre-event context was available.
     *
     * @return index of first infringing position in the trajectory arrays
     */
    public int getEventStartCycle() { return eventStartCycle; }

    /**
     * 0-based trajectory index of the last infringing cycle.
     *
     * <p>Set when {@link #enterObservationWindow()} is called. Returns {@code -1} as
     * a sentinel value before the observation window is entered (i.e. while the event
     * is still ACTIVE or in GRACE_PERIOD).
     *
     * @return index of last infringing position in the trajectory arrays, or {@code -1}
     */
    public int getEventEndCycle() { return eventEndCycle; }

    /**
     * Number of post-event positions appended via {@link #appendPostEventPosition} since
     * the observation window was entered.
     *
     * @return observation position count; zero before {@link #enterObservationWindow()}
     */
    public int getObservationPositionCount() { return observationPositionCount; }
}
