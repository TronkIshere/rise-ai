package com.aicore.simulator.report;

import com.aicore.simulator.evaluation.ScenarioResult;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class CategoryStats {

    private static final int MAX_DISTINCT_REASONS = 100;
    private static final int MAX_REASON_LENGTH = 160;

    private final int generated;
    private long completed;
    private long passed;
    private long failed;
    private long unscored;
    private long errors;
    private final Map<String, Long> failureReasons = new HashMap<>();

    public CategoryStats(int generated) {
        this.generated = generated;
    }

    public void record(ScenarioResult result) {
        completed++;
        switch (result.getStatus()) {
            case PASS -> passed++;
            case FAIL -> {
                failed++;
                countReason(result.getFailureReason());
            }
            case UNSCORED -> unscored++;
            case ERROR -> {
                errors++;
                countReason(result.getFailureReason());
            }
        }
    }

    public long getScored() {
        return passed + failed;
    }

    public List<Map.Entry<String, Long>> topFailureReasons(int limit) {
        return failureReasons.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .toList();
    }

    private void countReason(String reason) {
        String key = reasonKey(reason);
        if (!failureReasons.containsKey(key) && failureReasons.size() >= MAX_DISTINCT_REASONS) {
            key = "other";
        }
        failureReasons.merge(key, 1L, Long::sum);
    }

    static String reasonKey(String reason) {
        if (reason == null) {
            return "unknown";
        }
        String key = reason.replaceAll("^step \\d+: ", "");
        int separator = key.indexOf("; ");
        if (separator > 0) {
            key = key.substring(0, separator);
        }
        int detail = key.indexOf(" (");
        if (detail > 0 && (key.startsWith("TIMEOUT") || key.startsWith("CONNECTION_FAILED") || key.startsWith("IO_ERROR"))) {
            key = key.substring(0, detail);
        }
        return key.length() <= MAX_REASON_LENGTH ? key : key.substring(0, MAX_REASON_LENGTH);
    }
}
