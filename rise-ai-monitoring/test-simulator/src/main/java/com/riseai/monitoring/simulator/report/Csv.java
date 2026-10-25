package com.riseai.monitoring.simulator.report;

import java.util.StringJoiner;

public final class Csv {

    private Csv() {
    }

    public static String row(Object... values) {
        StringJoiner joiner = new StringJoiner(",", "", "\n");
        for (Object value : values) {
            joiner.add(escape(value == null ? "" : String.valueOf(value)));
        }
        return joiner.toString();
    }

    public static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
