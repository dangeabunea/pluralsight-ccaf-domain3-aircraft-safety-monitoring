package com.atcsafety.radar.loading;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration scoped to the radar loading pipeline.
 *
 * <p>A named bean is used rather than customising the global ObjectMapper
 * produced by Spring Boot's JacksonAutoConfiguration. The global mapper is
 * shared by Spring MVC, Actuator, and other auto-configured components —
 * enabling FAIL_ON_MISSING_PROPERTIES there would break their internal
 * deserialization. The named bean is private to RadarDumpLoader.
 *
 * <p>Decision logged in docs/team/decision-log.md (2026-03-21).
 */
@Configuration
public class RadarJacksonConfig {

    @Bean("radarObjectMapper")
    public ObjectMapper radarObjectMapper() {
        // Jackson 3 ObjectMapper is immutable — configuration goes through the builder.
        // Unknown fields (e.g. headingDeg) are handled by @JsonIgnoreProperties on RadarPosition.
        // Required field enforcement is handled by @JsonProperty(required = true) on each field.
        return JsonMapper.builder().build();
    }
}
