package com.riseai.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {
    private static final long SECOND = Duration.ofSeconds(1).toNanos();

    @Test
    void allowsUpToTheLimitThenRejectsWithinTheWindow() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire("a", 2, 0)).isTrue();
        assertThat(limiter.tryAcquire("a", 2, SECOND)).isTrue();
        assertThat(limiter.tryAcquire("a", 2, 2 * SECOND)).isFalse();
    }

    @Test
    void keysAreIndependentAndTheWindowResetsAfterItElapses() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire("a", 1, 0)).isTrue();
        assertThat(limiter.tryAcquire("a", 1, SECOND)).isFalse();
        assertThat(limiter.tryAcquire("b", 1, SECOND)).isTrue();
        assertThat(limiter.tryAcquire("a", 1, 11 * SECOND)).isTrue();
    }

    @Test
    void retryAfterReportsTheWindowLengthInWholeSeconds() {
        assertThat(new FixedWindowRateLimiter(Duration.ofMinutes(1)).retryAfterSeconds()).isEqualTo(60);
        assertThat(new FixedWindowRateLimiter(Duration.ofMillis(10)).retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void manyDistinctKeysShareAnOverflowBucketOnceTheCapIsReached() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Duration.ofHours(1));

        for (int i = 0; i < 100_000; i++) {
            limiter.tryAcquire("client-" + i, 5, 0);
        }

        boolean allowed = true;
        for (int i = 0; i < 6; i++) {
            allowed = limiter.tryAcquire("late-client", 5, 0);
        }
        assertThat(allowed).isFalse();
    }
}
