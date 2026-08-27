package com.atcsafety.radar.application;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ProcessingConfig} binds correctly to the
 * {@code radar.processing.*} property prefix.
 */
class ProcessingConfigTest {

    @Configuration
    @EnableConfigurationProperties(ProcessingConfig.class)
    static class MinimalConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinimalConfig.class);

    @Nested
    class InterCycleDelayMs {

        @Test
        void should_default_to_zero_when_property_is_not_set() {
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(ProcessingConfig.class).interCycleDelayMs())
                        .isZero();
            });
        }

        @Test
        void should_bind_configured_value_when_property_is_set() {
            contextRunner
                    .withPropertyValues("radar.processing.inter-cycle-delay-ms=5000")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(ProcessingConfig.class).interCycleDelayMs())
                                .isEqualTo(5000);
                    });
        }
    }
}
