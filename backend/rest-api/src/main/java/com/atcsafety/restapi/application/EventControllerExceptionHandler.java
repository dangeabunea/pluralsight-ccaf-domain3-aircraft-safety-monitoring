package com.atcsafety.restapi.application;

import com.atcsafety.restapi.domain.ReviewConflictException;
import com.atcsafety.restapi.domain.ReviewValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates application-layer validation exceptions to HTTP error responses
 * for the event query endpoints.
 */
@RestControllerAdvice
class EventControllerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EventControllerExceptionHandler.class);

    @ExceptionHandler(InvalidSortFieldException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse onInvalidSortField(InvalidSortFieldException ex) {
        log.warn("Rejected request with invalid sort field: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(InvalidStatusException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse onInvalidStatus(InvalidStatusException ex) {
        log.warn("Rejected request with invalid status parameter: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(CommentValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse onCommentValidation(CommentValidationException ex) {
        log.warn("Rejected comment request: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(EventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse onEventNotFound(EventNotFoundException ex) {
        log.warn("Event not found: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(ReviewValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse onReviewValidation(ReviewValidationException ex) {
        log.warn("Rejected review action — validation failed: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(ReviewConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse onReviewConflict(ReviewConflictException ex) {
        log.warn("Rejected review action — terminal state conflict: {}", ex.getMessage());
        return new ErrorResponse(ex.getMessage()
                + ". Re-opening dismissed or escalated events is not supported in this POC.");
    }

    record ErrorResponse(String error) {
    }
}
