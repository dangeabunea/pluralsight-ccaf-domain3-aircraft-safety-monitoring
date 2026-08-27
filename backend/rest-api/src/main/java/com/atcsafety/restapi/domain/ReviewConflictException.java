package com.atcsafety.restapi.domain;

/**
 * Thrown when a review transition is attempted on an event that is not in
 * {@link ReviewStatus#PENDING_REVIEW} state.
 *
 * <p>Maps to HTTP 409 Conflict. Once an event has been escalated or dismissed
 * it is in a terminal state and cannot be re-actioned.
 */
public class ReviewConflictException extends RuntimeException {

    public ReviewConflictException(String eventId, ReviewStatus currentStatus) {
        super("Review [" + eventId + "] cannot be actioned — current status is " + currentStatus
                + "; only PENDING_REVIEW events may be escalated or dismissed");
    }
}
