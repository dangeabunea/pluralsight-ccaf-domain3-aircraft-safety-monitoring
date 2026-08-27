package com.atcsafety.detection.infrastructure;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for persisted infringement events.
 *
 * <p>Spring Boot auto-configuration wires this interface at startup —
 * no implementation class is needed.
 */
interface MinSeparationInfringementRepository extends MongoRepository<MinSeparationInfringementDocument, String> {
}
