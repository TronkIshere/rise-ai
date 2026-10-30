package com.riseai.configuration;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class FixedWindowRateLimiter {
    private static final int SWEEP_THRESHOLD = 10_000;
    private static final int HARD_CAP = 100_000;
    private static final String OVERFLOW_KEY = "overflow";

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final long windowNanos;

    public FixedWindowRateLimiter(Duration window) {
        this.windowNanos = window.toNanos();
    }

    public boolean tryAcquire(String key, int limit, long nowNanos) {
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.values().removeIf(window -> nowNanos - window.start >= windowNanos);
        }
        String effectiveKey = windows.size() >= HARD_CAP && !windows.containsKey(key) ? OVERFLOW_KEY : key;
        Window window = windows.compute(effectiveKey, (k, current) -> {
            if (current == null || nowNanos - current.start >= windowNanos) {
                return new Window(nowNanos, 1);
            }
            return new Window(current.start, current.count + 1);
        });
        return window.count <= limit;
    }

    public long retryAfterSeconds() {
        return Math.max(1, Duration.ofNanos(windowNanos).toSeconds());
    }

    private record Window(long start, int count) {
    }
}
