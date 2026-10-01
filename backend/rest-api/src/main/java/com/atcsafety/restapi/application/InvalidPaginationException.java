package com.atcsafety.restapi.application;

/**
 * Thrown when a client supplies a {@code page} or {@code size} value outside the
 * supported range ({@code page >= 0}, {@code size >= 1}).
 */
public class InvalidPaginationException extends RuntimeException {

    public InvalidPaginationException(String message) {
        super(message);
    }
}
