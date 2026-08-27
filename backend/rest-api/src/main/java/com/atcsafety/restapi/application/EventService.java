package com.atcsafety.restapi.application;

import com.atcsafety.restapi.domain.InfringementReview;
import com.atcsafety.restapi.domain.ReviewStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for querying separation infringement events.
 *
 * <p>Owns two responsibilities: the CLOSED→PENDING_REVIEW status mapping (so the display
 * layer never sees raw MongoDB status values) and sort-field whitelist enforcement (so
 * arbitrary field names never reach the MongoDB query layer).
 */
@Service
public class EventService {

    // These are MongoDB document field names, not DTO field names
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("startedAt", "escalatedAt");
    private static final int MAX_COMMENT_TEXT_LENGTH = 1000;
    private static final int MAX_COMMENT_AUTHOR_LENGTH = 100;

    /**
     * MongoDB statuses that map to the PENDING_REVIEW display status.
     * "CLOSED" is written by the detection service; "PENDING_REVIEW" is written by
     * this service once a reviewer has acknowledged the event.
     */
    private static final List<String> PENDING_REVIEW_MONGO_STATUSES = List.of("CLOSED", "PENDING_REVIEW");

    private final InfringementEventPort eventPort;

    public EventService(InfringementEventPort eventPort) {
        this.eventPort = eventPort;
    }

    public EventSummaryResponse getSummary() {
        long pendingCount = eventPort.countWithMongoStatuses(PENDING_REVIEW_MONGO_STATUSES);
        long escalatedCount = eventPort.countWithMongoStatuses(List.of("ESCALATED"));
        return new EventSummaryResponse(pendingCount, escalatedCount);
    }

    public PagedResponse<EventListItemResponse> listEvents(
            String status, int page, int size, String sortField, String sortDirection) {
        validateSortField(sortField);
        Collection<String> mongoStatuses = mongoStatusesFor(status);
        long totalElements = eventPort.countWithMongoStatuses(mongoStatuses);
        int totalPages = (int) Math.ceil((double) totalElements / size);
        List<EventListItemResponse> items = eventPort
                .findPageWithMongoStatuses(mongoStatuses, page, size, sortField, sortDirection)
                .stream()
                .map(this::toListItem)
                .toList();
        return new PagedResponse<>(items, totalElements, totalPages, page, size);
    }

    private void validateSortField(String sortField) {
        if (sortField != null && !ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new InvalidSortFieldException(sortField);
        }
    }

    private Collection<String> mongoStatusesFor(String displayStatus) {
        return switch (displayStatus) {
            case "PENDING_REVIEW" -> PENDING_REVIEW_MONGO_STATUSES;
            case "ESCALATED" -> List.of("ESCALATED");
            case "DISMISSED" -> List.of("DISMISSED");
            default -> throw new InvalidStatusException(displayStatus);
        };
    }

    public EventDetailResponse getEventDetail(String id) {
        InfringementEventDetailData detail = eventPort.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        return toDetailResponse(detail);
    }

    private EventDetailResponse toDetailResponse(InfringementEventDetailData data) {
        String displayStatus = "CLOSED".equals(data.mongoStatus()) ? "PENDING_REVIEW" : data.mongoStatus();
        List<TrajectoryPositionData> trajectory1 = data.trajectory1().stream()
                .sorted(Comparator.comparingLong(TrajectoryPositionData::radarCycle))
                .toList();
        List<TrajectoryPositionData> trajectory2 = data.trajectory2().stream()
                .sorted(Comparator.comparingLong(TrajectoryPositionData::radarCycle))
                .toList();
        List<CommentData> comments = data.comments().stream()
                .sorted(Comparator.comparing(CommentData::createdAt).thenComparing(CommentData::id))
                .toList();
        return new EventDetailResponse(
                data.id(),
                data.firstAircraftCallsign(),
                data.secondAircraftCallsign(),
                data.startedAt(),
                data.endedAt(),
                displayStatus,
                data.escalationReason(),
                data.escalatedAt(),
                data.dismissedAt(),
                data.minHorizontalSeparationNm(),
                data.minVerticalSeparationFt(),
                data.minSeparationCycleIndex(),
                data.minVerticalSeparationCycleIndex(),
                data.eventStartCycle(),
                data.eventEndCycle(),
                trajectory1,
                trajectory2,
                comments);
    }

    public ChangeStatusResponse changeStatus(String id, String status, String escalationReason) {
        ReviewStatus targetStatus = parseReviewStatus(status);
        InfringementEventDetailData detail = eventPort.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        ReviewStatus currentStatus = parseReviewStatus(detail.mongoStatus());
        InfringementReview review = InfringementReview.reconstitute(id, currentStatus);
        review.transitionTo(targetStatus, escalationReason);
        eventPort.changeStatus(id, review.getStatus().name(), review.getEscalationReason(),
                review.getEscalatedAt(), review.getDismissedAt());
        return new ChangeStatusResponse(
                id,
                review.getStatus().name(),
                review.getEscalationReason(),
                review.getEscalatedAt(),
                review.getDismissedAt());
    }

    private ReviewStatus parseReviewStatus(String status) {
        return switch (status) {
            case "PENDING_REVIEW" -> ReviewStatus.PENDING_REVIEW;
            case "DISMISSED" -> ReviewStatus.DISMISSED;
            case "ESCALATED" -> ReviewStatus.ESCALATED;
            default -> throw new InvalidStatusException(status);
        };
    }

    public AddCommentResponse addComment(String eventId, String text, String author) {
        validateCommentText(text);
        validateCommentAuthor(author);
        String commentId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        boolean found = eventPort.addComment(eventId, new CommentData(commentId, text, author, createdAt));
        if (!found) {
            throw new EventNotFoundException(eventId);
        }
        return new AddCommentResponse(commentId, text, author, createdAt);
    }

    private void validateCommentText(String text) {
        if (text == null || text.isBlank()) {
            throw new CommentValidationException("Comment text must not be null or blank");
        }
        if (text.length() > MAX_COMMENT_TEXT_LENGTH) {
            throw new CommentValidationException(
                    "Comment text must not exceed " + MAX_COMMENT_TEXT_LENGTH + " characters");
        }
    }

    private void validateCommentAuthor(String author) {
        if (author == null || author.isBlank()) {
            throw new CommentValidationException("Comment author must not be null or blank");
        }
        if (author.length() > MAX_COMMENT_AUTHOR_LENGTH) {
            throw new CommentValidationException(
                    "Comment author must not exceed " + MAX_COMMENT_AUTHOR_LENGTH + " characters");
        }
    }

    private static final String EVENT_TYPE = "SeparationMinimaInfringement";

    private EventListItemResponse toListItem(InfringementEventData data) {
        String displayStatus = "CLOSED".equals(data.mongoStatus()) ? "PENDING_REVIEW" : data.mongoStatus();
        int commentCount = data.comments().size();
        String summary = buildSummary(data.minHorizontalSeparationNm(), data.minVerticalSeparationFt());
        return new EventListItemResponse(
                data.id(),
                data.firstAircraftCallsign(),
                data.secondAircraftCallsign(),
                data.startedAt(),
                data.endedAt(),
                displayStatus,
                data.escalationReason(),
                data.escalatedAt(),
                data.dismissedAt(),
                data.minHorizontalSeparationNm(),
                data.minVerticalSeparationFt(),
                commentCount,
                EVENT_TYPE,
                summary);
    }

    private String buildSummary(Double minHorizontalNm, Double minVerticalFt) {
        if (minHorizontalNm == null || minVerticalFt == null) {
            return null;
        }
        return String.format("Min sep: %.1f NM / %d ft", minHorizontalNm, minVerticalFt.intValue());
    }
}
