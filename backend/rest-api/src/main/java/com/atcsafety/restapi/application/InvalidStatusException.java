package com.atcsafety.restapi.application;

/**
 * Thrown when a client supplies a status value that is not one of the recognised
 * review statuses ({@code PENDING_REVIEW}, {@code ESCALATED}, {@code DISMISSED}).
 *
 * <p>Kept separate from {@link InvalidSortFieldException} so that API consumers
 * receive an accurate error message that identifies which parameter is invalid.
 */
public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String status) {
        super("Unknown status '" + status + "'. Allowed values: PENDING_REVIEW, ESCALATED, DISMISSED");
    }
}
