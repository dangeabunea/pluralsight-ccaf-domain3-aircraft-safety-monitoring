package com.atcsafety.restapi.application;

/**
 * Thrown by {@link EventService#getEventDetail(String)} when no event exists
 * for the requested id. Mapped to HTTP 404 by {@link EventControllerExceptionHandler}.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String id) {
        super("Event not found: " + id);
    }
}
