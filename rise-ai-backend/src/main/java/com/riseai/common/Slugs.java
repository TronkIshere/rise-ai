package com.riseai.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {
    private Slugs() {
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            return "item";
        }
        return slug.length() > 200 ? slug.substring(0, 200) : slug;
    }
}
