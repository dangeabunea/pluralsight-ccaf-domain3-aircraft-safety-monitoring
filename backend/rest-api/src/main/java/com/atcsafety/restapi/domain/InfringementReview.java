package com.atcsafety.restapi.domain;

import java.time.Instant;

/**
 * A safety review record for a closed separation infringement event.
 *
 * <p>Encapsulates the review lifecycle state machine:
 * {@code PENDING_REVIEW → ESCALATED} or {@code PENDING_REVIEW → DISMISSED}.
 * Both {@code ESCALATED} and {@code DISMISSED} are terminal states — an event
 * that has been actioned cannot be re-actioned.
 *
 * <p>Pure Java — zero framework imports. Fully unit-testable without a Spring context.
 *
 * <h2>Lifecycle</h2>
 * <p>Created via {@link #pendingReview(String)} when a closed detection event enters
 * the review queue. The application layer maps the detection service's {@code CLOSED}
 * status to {@code PENDING_REVIEW} in this domain.
 *
 * <h2>Escalation rule</h2>
 * <p>Escalation requires a non-blank reason of at most 1000 characters. In ATC safety
 * management, a referral to a higher authority must include documented justification so
 * that the receiving body can understand the grounds for escalation without additional
 * context. The 1000-character cap aligns with typical ATC safety report summary fields.
 */
public class InfringementReview {

    private static final int MAX_ESCALATION_REASON_LENGTH = 1000;

    private final String id;
    private ReviewStatus status;
    private String escalationReason;
    private Instant escalatedAt;
    private Instant dismissedAt;

    private InfringementReview(String id, ReviewStatus status) {
        this.id = id;
        this.status = status;
    }

    /**
     * Creates a new review in {@link ReviewStatus#PENDING_REVIEW} state.
     *
     * @param id the infringement event ID this review belongs to
     * @return a new review awaiting action
     */
    public static InfringementReview pendingReview(String id) {
        return new InfringementReview(id, ReviewStatus.PENDING_REVIEW);
    }

    /**
     * Rehydrates a review from a persisted document.
     *
     * <p>Use this factory when loading an existing event from MongoDB so that the
     * domain conflict guard ({@link #transitionTo}) operates on the correct current
     * status rather than always starting from {@code PENDING_REVIEW}.
     *
     * @param id            the infringement event ID
     * @param currentStatus the status as stored in MongoDB, mapped to {@link ReviewStatus}
     * @return a review object reflecting the persisted state
     */
    public static InfringementReview reconstitute(String id, ReviewStatus currentStatus) {
        return new InfringementReview(id, currentStatus);
    }

    /**
     * Transitions this review to the given target status.
     *
     * @param targetStatus     the desired next status
     * @param escalationReason required when {@code targetStatus} is {@link ReviewStatus#ESCALATED};
     *                         ignored (may be {@code null}) when dismissing
     * @throws ReviewConflictException    if the review is not in {@link ReviewStatus#PENDING_REVIEW} state
     * @throws ReviewValidationException  if escalating without a non-blank reason
     */
    public void transitionTo(ReviewStatus targetStatus, String escalationReason) {
        if (status != ReviewStatus.PENDING_REVIEW) {
            throw new ReviewConflictException(id, status);
        }
        if (targetStatus == ReviewStatus.ESCALATED) {
            validateEscalationReason(escalationReason);
            this.escalationReason = escalationReason;
            this.escalatedAt = Instant.now();
            this.status = ReviewStatus.ESCALATED;
        } else if (targetStatus == ReviewStatus.DISMISSED) {
            this.dismissedAt = Instant.now();
            this.status = ReviewStatus.DISMISSED;
        }
    }

    private void validateEscalationReason(String escalationReason) {
        if (escalationReason == null || escalationReason.isBlank()) {
            throw new ReviewValidationException(
                    "Escalation of review [" + id + "] requires a non-blank reason");
        }
        if (escalationReason.length() > MAX_ESCALATION_REASON_LENGTH) {
            throw new ReviewValidationException(
                    "Escalation reason for review [" + id + "] must not exceed "
                    + MAX_ESCALATION_REASON_LENGTH + " characters");
        }
    }

    public String getId() { return id; }
    public ReviewStatus getStatus() { return status; }
    public String getEscalationReason() { return escalationReason; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public Instant getDismissedAt() { return dismissedAt; }
}
