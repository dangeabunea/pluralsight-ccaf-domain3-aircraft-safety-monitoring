package com.atcsafety.detection;

import com.atcsafety.detection.application.DetectionConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DetectionConfig.class)
public class DetectionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DetectionServiceApplication.class, args);
    }
}
