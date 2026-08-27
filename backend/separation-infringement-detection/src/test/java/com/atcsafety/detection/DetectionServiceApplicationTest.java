package com.atcsafety.detection;

import com.atcsafety.detection.infrastructure.MinSeparationInfringementStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test — verifies that the Spring application context wires correctly.
 *
 * <p>MongoDB auto-configurations are excluded so no {@code MongoClient} is created
 * and no background monitor thread attempts a socket connection to {@code localhost:27017}.
 * {@code @MockitoBean} on {@link MinSeparationInfringementStore} registers a mock bean
 * replacing the real component, so Spring never requires the MongoDB repository.
 *
 * <p>Infrastructure-level tests (real MongoDB round-trips via Testcontainers) are in
 * {@link com.atcsafety.detection.infrastructure.MinSeparationInfringementStoreTest}.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ImportAutoConfiguration(exclude = {
        MongoAutoConfiguration.class,
        DataMongoAutoConfiguration.class,
        DataMongoRepositoriesAutoConfiguration.class
})
class DetectionServiceApplicationTest {

    @MockitoBean
    MinSeparationInfringementStore store;

    @Test
    void contextLoads() {
    }
}
