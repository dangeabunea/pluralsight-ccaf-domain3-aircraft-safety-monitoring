package com.atcsafety.radar.application;

/**
 * {@link InterCycleDelay} implementation that pauses the pipeline thread for a fixed
 * number of milliseconds between radar processing cycles.
 *
 * <p>Selected by {@link RadarPipelineConfig} when
 * {@code radar.processing.inter-cycle-delay-ms} is set to a positive value, allowing
 * the pipeline to replay the radar dump at real-time cadence.
 *
 * <p>If the thread is interrupted during the sleep, the interrupt flag is restored via
 * {@link Thread#interrupt()} before returning. The delay is abandoned for that cycle —
 * the caller's thread interrupt handling is left intact.
 */
public final class ThreadSleepDelay implements InterCycleDelay {

    private final int delayMs;

    public ThreadSleepDelay(int delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public void delay() {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            // Restore the interrupt flag so callers can observe and handle the interruption.
            Thread.currentThread().interrupt();
        }
    }
}
