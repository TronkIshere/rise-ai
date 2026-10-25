package com.riseai.monitoring.simulator.report;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Ratios {

    private Ratios() {
    }

    public static Double percent(long numerator, long denominator) {
        return denominator == 0 ? null : Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    public static String percentText(Double percent) {
        return percent == null ? "N/A" : String.format(Locale.US, "%.2f%%", percent);
    }

    public static String display(long numerator, long denominator) {
        return String.format(Locale.US, "%,d / %,d = %s", numerator, denominator, percentText(percent(numerator, denominator)));
    }

    public static Map<String, Object> ratio(long numerator, long denominator) {
        Map<String, Object> ratio = new LinkedHashMap<>();
        ratio.put("numerator", numerator);
        ratio.put("denominator", denominator);
        ratio.put("percent", percent(numerator, denominator));
        ratio.put("display", display(numerator, denominator));
        return ratio;
    }
}
