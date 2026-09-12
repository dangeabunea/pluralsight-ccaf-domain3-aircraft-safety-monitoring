package com.atcsafety.detection.application;

import com.atcsafety.detection.domain.MinSeparationEventLifecycleManager;
import com.atcsafety.detection.domain.MinSeparationEventStore;
import com.atcsafety.detection.domain.SeparationThresholds;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that {@link DetectionApplicationConfig} wires {@link DetectionConfig}
 * into downstream beans, applies defaults with warnings when properties are absent,
 * and fails fast when required properties are missing or invalid.
 *
 * <p>Uses {@link ApplicationContextRunner} for lightweight slice testing — no Kafka
 * broker, no MongoDB, no full application context startup.
 */
class DetectionApplicationConfigTest {

    /**
     * Minimal Spring configuration: binds {@link DetectionConfig} and imports the
     * production {@link DetectionApplicationConfig} and the in-memory registry.
     * Provides a mock {@link MinSeparationEventStore} so the lifecycle manager
     * bean can be wired without a MongoDB connection.
     *
     * <p>{@code @TestConfiguration} prevents this class from being picked up by
     * {@code @SpringBootTest} component scanning in other test classes.
     */
    @TestConfiguration
    @EnableConfigurationProperties(DetectionConfig.class)
    @Import({DetectionApplicationConfig.class, InMemoryMinSeparationActiveEventRegistry.class})
    static class MinimalConfig {

        @Bean
        public MinSeparationEventStore minSeparationEventStore() {
            return mock(MinSeparationEventStore.class);
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinimalConfig.class);

    // ---- Wiring with explicit property values ----

    @Nested
    class WiringWithExplicitValues {

        @Test
        void should_create_separation_thresholds_bean_when_all_properties_are_set() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=3",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(SeparationThresholds.class);
                    });
        }

        @Test
        void should_use_configured_horizontal_threshold_when_property_is_present() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=3.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=3",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var thresholds = context.getBean(SeparationThresholds.class);
                        assertThat(thresholds.horizontalThresholdNm()).isEqualTo(3.0);
                    });
        }

        @Test
        void should_use_configured_vertical_threshold_when_property_is_present() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=800",
                            "detection.gracePeriodCycles=3",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var thresholds = context.getBean(SeparationThresholds.class);
                        assertThat(thresholds.verticalThresholdFt()).isEqualTo(800);
                    });
        }

        @Test
        void should_expose_kafka_topic_bean_when_topic_property_is_present() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=3",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("detectionKafkaTopic", String.class))
                                .isEqualTo("radar.validated-positions");
                    });
        }

        @Test
        void should_use_grace_period_of_five_when_configured_as_five() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=5",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("gracePeriodCycles", Integer.class))
                                .isEqualTo(5);
                    });
        }
    }

    // ---- Default fallback when optional properties are absent ----

    @Nested
    class DefaultFallback {

        @Test
        void should_start_and_use_default_horizontal_threshold_when_property_is_absent() {
            contextRunner
                    .withPropertyValues(
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=3",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var thresholds = context.getBean(SeparationThresholds.class);
                        assertThat(thresholds.horizontalThresholdNm()).isEqualTo(5.0);
                    });
        }

        @Test
        void should_start_and_use_default_vertical_threshold_when_property_is_absent() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.gracePeriodCycles=3",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var thresholds = context.getBean(SeparationThresholds.class);
                        assertThat(thresholds.verticalThresholdFt()).isEqualTo(1000);
                    });
        }

        @Test
        void should_start_and_use_default_grace_period_when_property_is_absent() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("gracePeriodCycles", Integer.class))
                                .isEqualTo(3);
                    });
        }

        @Test
        void should_start_when_all_optional_properties_are_absent_but_kafka_topic_is_set() {
            contextRunner
                    .withPropertyValues("detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var thresholds = context.getBean(SeparationThresholds.class);
                        assertThat(thresholds.horizontalThresholdNm()).isEqualTo(5.0);
                        assertThat(thresholds.verticalThresholdFt()).isEqualTo(1000);
                        assertThat(context.getBean("gracePeriodCycles", Integer.class)).isEqualTo(3);
                    });
        }
    }

    // ---- Startup failure on invalid or missing required properties ----

    @Nested
    class StartupFailure {

        @Test
        void should_fail_startup_when_grace_period_cycles_is_zero() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=0",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("detection.gracePeriodCycles")
                                .hasMessageContaining("0");
                    });
        }

        @Test
        void should_fail_startup_when_grace_period_cycles_is_negative() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=-1",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("detection.gracePeriodCycles");
                    });
        }

        @Test
        void should_fail_startup_when_kafka_topic_is_absent() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=3")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("detection.kafka.topic");
                    });
        }

        @Test
        void should_fail_startup_when_kafka_topic_is_blank() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=3",
                            "detection.kafka.topic=   ")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("detection.kafka.topic");
                    });
        }
    }

    // ---- Boundary values ----

    @Nested
    class BoundaryValues {

        @Test
        void should_start_when_grace_period_cycles_is_one() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=1",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("gracePeriodCycles", Integer.class))
                                .isEqualTo(1);
                    });
        }
    }

    // ---- Lifecycle manager wiring ----

    @Nested
    class LifecycleManagerWiring {

        @Test
        void should_create_lifecycle_manager_bean_when_all_dependencies_are_present() {
            contextRunner
                    .withPropertyValues(
                            "detection.horizontalThresholdNm=5.0",
                            "detection.verticalThresholdFt=1000",
                            "detection.gracePeriodCycles=3",
                            "detection.kafka.topic=radar.validated-positions")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(MinSeparationEventLifecycleManager.class);
                    });
        }
    }
}
