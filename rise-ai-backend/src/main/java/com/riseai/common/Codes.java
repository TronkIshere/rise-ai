package com.riseai.common;

import java.security.SecureRandom;

public final class Codes {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private Codes() {
    }

    public static String generate(String prefix) {
        StringBuilder builder = new StringBuilder(prefix).append('-');
        for (int i = 0; i < 12; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
