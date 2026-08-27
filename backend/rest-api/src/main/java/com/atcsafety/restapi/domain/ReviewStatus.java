package com.atcsafety.restapi.domain;

/**
 * Lifecycle states of an infringement review.
 *
 * <p>{@code PENDING_REVIEW} is the initial state — mapped from the detection service's
 * {@code CLOSED} status when the event enters the review queue. Only events in
 * {@code PENDING_REVIEW} may be actioned.
 *
 * <p>{@code ESCALATED} means the reviewing authority has referred the occurrence to a
 * higher-authority body (e.g., safety department, national aviation authority). An
 * escalation reason is mandatory.
 *
 * <p>{@code DISMISSED} means the reviewing authority determined no further action is
 * required for this occurrence.
 */
public enum ReviewStatus {
    PENDING_REVIEW,
    ESCALATED,
    DISMISSED
}
