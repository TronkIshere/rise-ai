package com.aicore.eventmonitor.configuration;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ollama")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OllamaProperties {

    @NotNull
    URI baseUrl = URI.create("http://localhost:11434");

    String model;

    @NotNull
    Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    Duration requestTimeout = Duration.ofSeconds(60);

    @NotNull
    Duration statusTimeout = Duration.ofSeconds(3);

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    double temperature = 0.0;

    @Min(16)
    int maxOutputTokens = 512;

    Boolean think = Boolean.FALSE;

    @Min(512)
    Integer contextLength;
}
