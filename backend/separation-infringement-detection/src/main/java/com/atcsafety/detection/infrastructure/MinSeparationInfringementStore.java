package com.atcsafety.detection.infrastructure;

import com.atcsafety.detection.domain.MinSeparationEventStore;
import com.atcsafety.detection.domain.MinSeparationInfringementEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MongoDB-backed implementation of the {@link MinSeparationEventStore} domain port.
 *
 * <p>Translates the pure-Java {@link MinSeparationInfringementEvent} aggregate to a
 * {@link MinSeparationInfringementDocument} and delegates to
 * {@link MinSeparationInfringementRepository}.
 *
 * <p>Trajectory arrays are mapped from domain {@link com.atcsafety.detection.domain.TrajectoryPosition}
 * values to embedded {@link TrajectoryPositionDocument} instances, preserving the
 * DDD infrastructure/domain layer boundary.
 */
@Component
public class MinSeparationInfringementStore implements MinSeparationEventStore {

    private final MinSeparationInfringementRepository repository;

    MinSeparationInfringementStore(MinSeparationInfringementRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(MinSeparationInfringementEvent event) {
        try {
            repository.save(toDocument(event));
        } catch (RuntimeException ex) {
            throw new RuntimeException(
                    "Failed to persist infringement event [" + event.getEventId() + "] " +
                    "for pair " + event.getCallsign1() + "/" + event.getCallsign2() +
                    " starting cycle " + event.getStartCycle(), ex);
        }
    }

    private MinSeparationInfringementDocument toDocument(MinSeparationInfringementEvent event) {
        var trajectory1 = event.getTrajectory1().stream()
                .map(TrajectoryPositionDocument::from)
                .toList();
        var trajectory2 = event.getTrajectory2().stream()
                .map(TrajectoryPositionDocument::from)
                .toList();
        return new MinSeparationInfringementDocument(
                event.getEventId(),
                event.getCallsign1(),
                event.getCallsign2(),
                event.getStartCycle(),
                event.getStartTime(),
                event.getLastActiveCycleTimestamp(),
                event.getStatus(),
                trajectory1,
                trajectory2,
                event.getMinHorizontalSeparationNm(),
                event.getMinVerticalSeparationFt(),
                event.getMinSeparationCycleIndex(),
                event.getMinVerticalSeparationCycleIndex(),
                event.getEventStartCycle(),
                event.getEventEndCycle());
    }
}
