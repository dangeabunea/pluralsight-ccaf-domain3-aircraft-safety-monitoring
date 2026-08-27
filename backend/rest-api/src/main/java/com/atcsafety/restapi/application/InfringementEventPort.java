package com.atcsafety.restapi.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Port through which the application layer reads and writes infringement event documents.
 *
 * <p>Accepts raw MongoDB status strings (e.g. {@code "CLOSED"}, {@code "PENDING_REVIEW"})
 * so the caller controls which status values to query — including the CLOSED→PENDING_REVIEW
 * mapping for pending counts. The adapter in {@code infrastructure/} implements this port
 * against Spring Data MongoDB.
 *
 * <p><strong>Note on mixed responsibility:</strong> This port combines read (query) and
 * write (status change, add comment) operations. In a larger system these would be separated
 * into a query port and a command port following CQRS principles. For this POC the unified
 * port is an acceptable simplification.
 */
public interface InfringementEventPort {

    /**
     * Returns the count of documents whose {@code status} field is in {@code mongoStatuses}.
     */
    long countWithMongoStatuses(Collection<String> mongoStatuses);

    /**
     * Returns one page of documents whose {@code status} field is in {@code mongoStatuses},
     * sorted by {@code sortField} in {@code sortDirection} order.
     *
     * @param sortField     MongoDB field name to sort by, or {@code null} for default ordering
     * @param sortDirection {@code "asc"} or {@code "desc"}
     */
    List<InfringementEventData> findPageWithMongoStatuses(
            Collection<String> mongoStatuses, int page, int size, String sortField, String sortDirection);

    /**
     * Returns the full detail of a single event including both aircraft trajectories and comments,
     * or {@link Optional#empty()} if no document exists for {@code id}.
     */
    Optional<InfringementEventDetailData> findById(String id);

    /**
     * Atomically appends {@code comment} to the event's {@code comments} array via a
     * single MongoDB {@code $push} — no document load required.
     *
     * @return {@code true} if the event was found and the comment appended;
     *         {@code false} if no document exists for {@code eventId}
     */
    boolean addComment(String eventId, CommentData comment);

    /**
     * Updates the review lifecycle fields of a single event document via MongoDB {@code $set}.
     *
     * <p>Only the four review fields ({@code status}, {@code escalationReason},
     * {@code escalatedAt}, {@code dismissedAt}) are written — the detection-service fields
     * (trajectory arrays, separation metrics, start/end times) are not touched.
     *
     * <p>Callers must pass timestamps produced by the domain object after {@code transitionTo()}
     * completes; they must not generate timestamps here (see architecture decision: timestamp
     * ownership in domain).
     *
     * @param id               the event document ID
     * @param newStatus        the post-transition review status, as a string matching the MongoDB field
     * @param escalationReason the reason for escalation, or {@code null} when dismissing
     * @param escalatedAt      the UTC instant of escalation, or {@code null} when dismissing
     * @param dismissedAt      the UTC instant of dismissal, or {@code null} when escalating
     * @return {@code true} if the event was found and updated;
     *         {@code false} if no document exists for {@code id}
     */
    boolean changeStatus(String id, String newStatus, String escalationReason,
                         Instant escalatedAt, Instant dismissedAt);
}
