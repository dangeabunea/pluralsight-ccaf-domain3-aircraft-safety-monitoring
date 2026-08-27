package com.atcsafety.radar.application;

/**
 * Strategy for the pause applied between radar processing cycles.
 *
 * <p>Two implementations are wired by {@link RadarPipelineConfig}:
 * <ul>
 *   <li>{@link NoOpDelay} — returns immediately; used when
 *       {@code radar.processing.inter-cycle-delay-ms} is absent or zero.
 *   <li>{@link ThreadSleepDelay} — sleeps for the configured number of milliseconds;
 *       used when the property is set to a positive value.
 * </ul>
 *
 * <p>The pipeline calls {@link #delay()} once at each real-cycle boundary (after all
 * positions of cycle N are published and before the first position of cycle N+1 is
 * processed). No delay is applied before the flush sentinel.
 */
@FunctionalInterface
public interface InterCycleDelay {

    /**
     * Applies the inter-cycle pause. Returns normally in all cases — implementations
     * must handle {@link InterruptedException} internally and restore the interrupt flag.
     */
    void delay();
}
