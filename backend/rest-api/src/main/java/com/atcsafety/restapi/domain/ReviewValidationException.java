package com.atcsafety.restapi.domain;

/**
 * Thrown when a review transition request violates a business validation rule.
 *
 * <p>Maps to HTTP 400 Bad Request. The canonical case is attempting to escalate
 * a review without providing a reason — escalation without documented justification
 * is not permitted in ATC safety management.
 */
public class ReviewValidationException extends RuntimeException {

    public ReviewValidationException(String message) {
        super(message);
    }
}
