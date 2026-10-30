package com.riseai.monitoring.analysis;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class FindingNoteFilter {

    private static final List<String> FORBIDDEN_TERMS = List.of("fraud", "theft", "stole", "steal", "criminal", "scam", "embezzl",
            "misuse confirmed", "definitely", "proves", "guilty", "illegal", "block the", "release the", "refund the", "must be paid",
            "ignore previous", "ignore all", "ignore the", "disregard", "instruction", "approve", "system prompt", "as an ai");
    private static final Pattern IDENTIFIER = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|\\b[0-9a-f]{32,}\\b|https?://|\\S+@\\S+\\.\\S+");

    private FindingNoteFilter() {
    }

    public static Optional<String> accept(String candidate, int maxLength) {
        if (candidate == null) {
            return Optional.empty();
        }
        String note = candidate.strip().replaceAll("\\s+", " ");
        if (note.isEmpty() || note.length() > maxLength) {
            return Optional.empty();
        }
        String lower = note.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_TERMS.stream().anyMatch(lower::contains) || IDENTIFIER.matcher(note).find()) {
            return Optional.empty();
        }
        return Optional.of(note);
    }
}
