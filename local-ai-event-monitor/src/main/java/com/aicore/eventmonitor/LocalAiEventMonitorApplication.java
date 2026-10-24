package com.aicore.eventmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LocalAiEventMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalAiEventMonitorApplication.class, args);
    }
}
