package com.atcsafety.restapi.infrastructure;

import com.atcsafety.restapi.application.CommentData;
import com.atcsafety.restapi.application.InfringementEventData;
import com.atcsafety.restapi.application.InfringementEventDetailData;
import com.atcsafety.restapi.application.InfringementEventPort;
import com.atcsafety.restapi.application.TrajectoryPositionData;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Adapter that implements {@link InfringementEventPort} against the MongoDB
 * {@code separation_infringement_events} collection via Spring Data.
 *
 * <p>Maps {@link InfringementEventDocument} to {@link InfringementEventData} —
 * the only place in the infrastructure layer that knows about both types.
 */
@Component
class InfringementEventQueryAdapter implements InfringementEventPort {

    private static final String DEFAULT_SORT_FIELD = "startedAt";

    private final InfringementEventRepository repository;
    private final MongoTemplate mongoTemplate;

    InfringementEventQueryAdapter(InfringementEventRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long countWithMongoStatuses(Collection<String> mongoStatuses) {
        return repository.countByStatusIn(mongoStatuses);
    }

    @Override
    public List<InfringementEventData> findPageWithMongoStatuses(
            Collection<String> mongoStatuses, int page, int size, String sortField, String sortDirection) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortField, sortDirection));
        return repository.findByStatusIn(mongoStatuses, pageable)
                .stream()
                .map(this::toEventData)
                .toList();
    }

    private Sort buildSort(String sortField, String sortDirection) {
        String field = (sortField != null) ? sortField : DEFAULT_SORT_FIELD;
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }

    @Override
    public Optional<InfringementEventDetailData> findById(String id) {
        return repository.findById(id).map(this::toEventDetailData);
    }

    @Override
    public boolean addComment(String eventId, CommentData comment) {
        Query query = Query.query(Criteria.where("_id").is(eventId));
        Update update = new Update().push("comments",
                new CommentDocument(comment.id(), comment.text(), comment.author(), comment.createdAt()));
        UpdateResult result = mongoTemplate.updateFirst(query, update, InfringementEventDocument.class);
        return result.getMatchedCount() == 1;
    }

    @Override
    public boolean changeStatus(String id, String newStatus, String escalationReason,
                                Instant escalatedAt, Instant dismissedAt) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("status", newStatus)
                .set("escalationReason", escalationReason)
                .set("escalatedAt", escalatedAt)
                .set("dismissedAt", dismissedAt);
        UpdateResult result = mongoTemplate.updateFirst(query, update, InfringementEventDocument.class);
        return result.getMatchedCount() == 1;
    }

    private InfringementEventDetailData toEventDetailData(InfringementEventDocument doc) {
        return new InfringementEventDetailData(
                doc.getId(),
                doc.getFirstAircraftCallsign(),
                doc.getSecondAircraftCallsign(),
                doc.getStartedAt(),
                doc.getEndedAt(),
                doc.getStatus(),
                doc.getEscalationReason(),
                doc.getEscalatedAt(),
                doc.getDismissedAt(),
                doc.getMinHorizontalSeparationNm(),
                doc.getMinVerticalSeparationFt(),
                doc.getMinSeparationCycleIndex(),
                doc.getMinVerticalSeparationCycleIndex(),
                doc.getEventStartCycle(),
                doc.getEventEndCycle(),
                mapTrajectory(doc.getTrajectory1()),
                mapTrajectory(doc.getTrajectory2()),
                mapComments(doc.getComments()));
    }

    private List<TrajectoryPositionData> mapTrajectory(List<TrajectoryPositionDocument> docs) {
        if (docs == null) return List.of();
        return docs.stream()
                .map(d -> new TrajectoryPositionData(
                        d.getRadarCycle(),
                        d.getCycleTimestamp(),
                        d.getX(),
                        d.getY(),
                        d.getAltFeet(),
                        d.getLat(),
                        d.getLon(),
                        d.getSpeedKn()))
                .toList();
    }

    private List<CommentData> mapComments(List<CommentDocument> docs) {
        if (docs == null) return List.of();
        return docs.stream()
                .map(d -> new CommentData(d.getId(), d.getText(), d.getAuthor(), d.getCreatedAt()))
                .toList();
    }

    private InfringementEventData toEventData(InfringementEventDocument doc) {
        return new InfringementEventData(
                doc.getId(),
                doc.getFirstAircraftCallsign(),
                doc.getSecondAircraftCallsign(),
                doc.getStartedAt(),
                doc.getEndedAt(),
                doc.getStatus(),
                doc.getEscalationReason(),
                doc.getEscalatedAt(),
                doc.getDismissedAt(),
                doc.getMinHorizontalSeparationNm(),
                doc.getMinVerticalSeparationFt(),
                mapComments(doc.getComments()));
    }
}
