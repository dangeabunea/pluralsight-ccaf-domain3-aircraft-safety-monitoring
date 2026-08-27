package com.atcsafety.restapi.application;

/**
 * Thrown when a client supplies a sort field that is not on the whitelist.
 *
 * <p>Passing an arbitrary field name to MongoDB would expose schema structure and
 * open the door to data-ordering attacks. The whitelist in {@link EventService}
 * throws this exception before any query is issued, and the exception handler
 * maps it to HTTP 400.
 */
public class InvalidSortFieldException extends RuntimeException {

    public InvalidSortFieldException(String field) {
        super("Unknown sort field '" + field + "'. Allowed values: startedAt, escalatedAt");
    }
}
