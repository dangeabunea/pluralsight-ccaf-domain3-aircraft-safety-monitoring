package com.atcsafety.radar.loading;

/**
 * Thrown by RadarDumpLoader when the configured radar dump file cannot be
 * found or read. The message always contains the configured file path so the
 * operator can diagnose the problem immediately from the startup log.
 */
public class RadarDumpFileNotFoundException extends RuntimeException {

    public RadarDumpFileNotFoundException(String message) {
        super(message);
    }

    public RadarDumpFileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
