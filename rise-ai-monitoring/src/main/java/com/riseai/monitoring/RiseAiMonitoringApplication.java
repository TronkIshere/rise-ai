package com.riseai.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RiseAiMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiseAiMonitoringApplication.class, args);
    }
}
