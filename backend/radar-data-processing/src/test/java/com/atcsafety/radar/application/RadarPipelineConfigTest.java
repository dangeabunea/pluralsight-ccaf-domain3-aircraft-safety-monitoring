package com.atcsafety.radar.application;

import com.atcsafety.radar.enrichment.CoordinateDeriver;
import com.atcsafety.radar.loading.RadarJacksonConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link RadarPipelineConfig} correctly wires {@link RadarConfig} and
 * {@link KafkaProducerConfig} into downstream beans, and that the application context
 * fails fast when required properties are absent or out of range.
 */
class RadarPipelineConfigTest {

    /**
     * Minimal Spring configuration for these tests: binds {@link RadarConfig},
     * {@link KafkaProducerConfig}, and {@link ProcessingConfig}, then imports the
     * production {@link RadarPipelineConfig} under test. {@link RadarJacksonConfig} is
     * also imported to satisfy the {@code radarObjectMapper} dependency. No Kafka broker,
     * no file I/O, no full application context.
     */
    @Configuration
    @EnableConfigurationProperties({RadarConfig.class, KafkaProducerConfig.class, ProcessingConfig.class})
    @Import({RadarPipelineConfig.class, RadarJacksonConfig.class})
    static class MinimalConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinimalConfig.class);

    // ---- Wiring ----

    @Nested
    class Wiring {

        @Test
        void should_create_coordinate_deriver_bean_when_both_coords_are_present() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=48.8566", "radar.lon=2.3522",
                            "radar.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(CoordinateDeriver.class);
                    });
        }
    }

    // ---- Startup failure on missing properties ----

    @Nested
    class StartupFailure {

        @Test
        void should_fail_startup_when_radar_lat_is_missing() {
            contextRunner
                    .withPropertyValues(
                            "radar.lon=2.3522",
                            "radar.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("radar.lat");
                    });
        }

        @Test
        void should_fail_startup_when_radar_lon_is_missing() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=48.8566",
                            "radar.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("radar.lon");
                    });
        }

        @Test
        void should_fail_startup_when_both_radar_coords_are_missing() {
            contextRunner
                    .withPropertyValues("radar.kafka.topic=radar.validated-positions")
                    .run(context ->
                            assertThat(context).hasFailed()
                    );
        }

        @Test
        void should_fail_startup_when_lat_is_above_maximum() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=91.0", "radar.lon=2.3522",
                            "radar.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("Invalid radar.lat: 91.0");
                    });
        }

        @Test
        void should_fail_startup_when_lon_is_below_minimum() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=48.8566", "radar.lon=-181.0",
                            "radar.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("Invalid radar.lon: -181.0");
                    });
        }
    }

    // ---- Boundary values ----

    @Nested
    class BoundaryValues {

        @Test
        void should_start_normally_when_lat_is_at_maximum_boundary() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=90.0", "radar.lon=2.3522",
                            "radar.kafka.topic=radar.validated-positions")
                    .run(context -> assertThat(context).hasNotFailed());
        }

        @Test
        void should_start_normally_when_lon_is_at_minimum_boundary() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=48.8566", "radar.lon=-180.0",
                            "radar.kafka.topic=radar.validated-positions")
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }

    // ---- Kafka topic ----

    @Nested
    class KafkaTopic {

        @Test
        void should_create_kafka_topic_bean_when_topic_is_present() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=48.8566", "radar.lon=2.3522",
                            "radar.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("kafkaTopic", String.class))
                                .isEqualTo("radar.validated-positions");
                    });
        }

        @Test
        void should_fail_startup_when_radar_kafka_topic_is_missing() {
            contextRunner
                    .withPropertyValues("radar.lat=48.8566", "radar.lon=2.3522")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("radar.kafka.topic");
                    });
        }
    }

    // ---- Inter-cycle delay bean ----

    @Nested
    class InterCycleDelayBean {

        @Test
        void should_return_no_op_delay_when_delay_is_zero() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=48.8566", "radar.lon=2.3522",
                            "radar.kafka.topic=radar.validated-positions",
                            "radar.processing.inter-cycle-delay-ms=0")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(InterCycleDelay.class))
                                .isInstanceOf(NoOpDelay.class);
                    });
        }

        @Test
        void should_return_thread_sleep_delay_when_delay_is_positive() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=48.8566", "radar.lon=2.3522",
                            "radar.kafka.topic=radar.validated-positions",
                            "radar.processing.inter-cycle-delay-ms=5000")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(InterCycleDelay.class))
                                .isInstanceOf(ThreadSleepDelay.class);
                    });
        }

        @Test
        void should_throw_illegal_state_when_delay_is_negative() {
            contextRunner
                    .withPropertyValues(
                            "radar.lat=48.8566", "radar.lon=2.3522",
                            "radar.kafka.topic=radar.validated-positions",
                            "radar.processing.inter-cycle-delay-ms=-1")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("inter-cycle-delay-ms");
                    });
        }
    }
}
