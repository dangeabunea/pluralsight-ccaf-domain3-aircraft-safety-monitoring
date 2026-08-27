package com.atcsafety.radar.application;

/**
 * {@link InterCycleDelay} implementation that returns immediately.
 *
 * <p>Selected by {@link RadarPipelineConfig} when
 * {@code radar.processing.inter-cycle-delay-ms} is absent or zero, enabling the pipeline
 * to process the radar dump at maximum speed for testing and demo scenarios.
 */
public final class NoOpDelay implements InterCycleDelay {

    @Override
    public void delay() {
        // intentional no-op: zero-wait mode
    }
}
