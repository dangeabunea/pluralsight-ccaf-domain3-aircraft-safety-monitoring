package com.atcsafety.radar;

import com.atcsafety.radar.application.KafkaProducerConfig;
import com.atcsafety.radar.application.ProcessingConfig;
import com.atcsafety.radar.application.RadarConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RadarConfig.class, KafkaProducerConfig.class, ProcessingConfig.class})
public class RadarDataProcessingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RadarDataProcessingApplication.class, args);
    }
}
