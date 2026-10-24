package com.aicore.eventmonitor.dto.request.event;

import com.aicore.eventmonitor.constants.PlatformConstants;
import com.aicore.eventmonitor.core.event.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonitoringEventRequest {

    @NotBlank(message = "eventId must not be blank")
    @Size(max = 128, message = "eventId must not exceed 128 characters")
    @Pattern(regexp = PlatformConstants.IDENTIFIER_REGEX, message = "eventId may contain only letters, digits and . _ : -")
    String eventId;

    @NotBlank(message = "eventType must not be blank")
    @Size(max = 100, message = "eventType must not exceed 100 characters")
    @Pattern(regexp = PlatformConstants.IDENTIFIER_REGEX, message = "eventType may contain only letters, digits and . _ : -")
    String eventType;

    @NotBlank(message = "source must not be blank")
    @Size(max = 100, message = "source must not exceed 100 characters")
    @Pattern(regexp = PlatformConstants.IDENTIFIER_REGEX, message = "source may contain only letters, digits and . _ : -")
    String source;

    @NotNull(message = "occurredAt is required")
    Instant occurredAt;

    Instant receivedAt;

    @Pattern(regexp = PlatformConstants.CORRELATION_ID_REGEX, message = "correlationId must be 1-64 letters, digits or . _ : -")
    String correlationId;

    Severity severity;

    @Size(max = 200, message = "No more than 200 attributes are allowed")
    Map<@NotBlank(message = "attribute names must not be blank")
        @Size(max = 64, message = "attribute names must not exceed 64 characters")
        @Pattern(regexp = PlatformConstants.IDENTIFIER_REGEX, message = "attribute names may contain only letters, digits and . _ : -")
        String, Object> attributes;

    JsonNode payload;
}
