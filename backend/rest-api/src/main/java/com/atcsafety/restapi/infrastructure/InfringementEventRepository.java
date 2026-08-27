package com.atcsafety.restapi.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data MongoDB repository for reading and updating infringement event documents.
 */
public interface InfringementEventRepository extends MongoRepository<InfringementEventDocument, String> {

    /**
     * Counts documents whose {@code status} field is one of the given values.
     * Used by {@link InfringementEventQueryAdapter} to compute summary counts,
     * including the CLOSED→PENDING_REVIEW mapping that requires querying two status values.
     */
    long countByStatusIn(Collection<String> statuses);

    /**
     * Returns a sorted, offset-limited page of documents whose {@code status} field
     * is one of the given values. Sorting and pagination are applied via {@code pageable}.
     */
    List<InfringementEventDocument> findByStatusIn(Collection<String> statuses, Pageable pageable);
}
