package com.aicore.simulator.scenario;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public enum Category {

    NORMAL("A", "Normal events", "Valid events that must complete without rule findings", 25, false),
    RULE_VIOLATION("B", "Deterministic rule violations", "Events that must trigger a specific implemented rule", 15, false),
    BOUNDARY("C", "Boundary values", "Values just below, at and above implemented limits and thresholds", 15, false),
    AI_CONTEXT("D", "AI context interpretation", "Flagged events whose AI assessment is scored against a predefined rubric", 5, true),
    INVALID("E", "Invalid or malformed events", "Requests that violate the documented API validation contract", 10, false),
    DUPLICATE("F", "Duplicate events", "Repeated event IDs checked against the documented idempotency behaviour", 8, false),
    OPTIONAL_FIELDS("G", "Missing and optional fields", "Valid optional-field variations that must be accepted", 7, false),
    EVENT_SEQUENCE("H", "Event sequences", "Ordered related events sharing a correlation ID, evaluated per event", 5, false),
    LARGE_PAYLOAD("I", "Large and unusual valid payloads", "Valid payloads of varying size, depth and character sets within limits", 5, false),
    AI_RESILIENCE("J", "AI failure resilience", "Flagged events checked for the AI failure contract in whatever AI state the Core is in", 5, true);

    private final String code;
    private final String label;
    private final String description;
    private final int defaultWeight;
    private final boolean aiDependent;

    Category(String code, String label, String description, int defaultWeight, boolean aiDependent) {
        this.code = code;
        this.label = label;
        this.description = description;
        this.defaultWeight = defaultWeight;
        this.aiDependent = aiDependent;
    }

    public static Map<Category, Integer> defaultDistribution() {
        Map<Category, Integer> distribution = new LinkedHashMap<>();
        for (Category category : values()) {
            distribution.put(category, category.defaultWeight);
        }
        return distribution;
    }
}
