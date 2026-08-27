package com.atcsafety.restapi.application;

/**
 * Thrown when a submitted comment fails text validation — null, blank, or exceeds the
 * maximum allowed character count. Mapped to HTTP 400 by {@link EventControllerExceptionHandler}.
 */
public class CommentValidationException extends RuntimeException {

    public CommentValidationException(String reason) {
        super(reason);
    }
}
