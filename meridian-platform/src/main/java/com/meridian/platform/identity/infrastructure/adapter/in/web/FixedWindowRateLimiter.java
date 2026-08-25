package com.meridian.platform.identity.infrastructure.adapter.in.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class FixedWindowRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final int maxTrackedKeys;
    private final Clock clock;
    private final Map<String, WindowState> windows = new LinkedHashMap<>(16, 0.75f, true);

    FixedWindowRateLimiter(int maxRequests, Duration window, int maxTrackedKeys, Clock clock, String policyName) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException(policyName + " rate-limit maximum requests must be positive");
        }
        this.window = requirePositive(window, policyName + " rate-limit window");
        if (maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("rate-limit maximum tracked keys must be positive");
        }
        this.maxRequests = maxRequests;
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    synchronized RateLimitDecision acquire(String key) {
        Objects.requireNonNull(key, "rate-limit key must not be null");
        Instant now = clock.instant();
        WindowState current = windows.get(key);
        if (current == null || !now.isBefore(current.resetsAt())) {
            windows.put(key, new WindowState(1, now.plus(window)));
            evictEldestEntries();
            return RateLimitDecision.allow();
        }

        if (current.requestCount() < maxRequests) {
            windows.put(key, new WindowState(current.requestCount() + 1, current.resetsAt()));
            return RateLimitDecision.allow();
        }

        return RateLimitDecision.rejected(wholeSecondsUntil(now, current.resetsAt()));
    }

    synchronized int trackedKeyCount() {
        return windows.size();
    }

    private void evictEldestEntries() {
        Iterator<String> keys = windows.keySet().iterator();
        while (windows.size() > maxTrackedKeys && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private static long wholeSecondsUntil(Instant now, Instant resetsAt) {
        Duration remaining = Duration.between(now, resetsAt);
        long seconds = remaining.getSeconds();
        if (remaining.getNano() > 0) {
            seconds++;
        }
        return Math.max(1, seconds);
    }

    private static Duration requirePositive(Duration duration, String propertyName) {
        Objects.requireNonNull(duration, propertyName + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
        return duration;
    }

    private record WindowState(int requestCount, Instant resetsAt) {
    }

    record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        static RateLimitDecision allow() {
            return new RateLimitDecision(true, 0);
        }

        static RateLimitDecision rejected(long retryAfterSeconds) {
            return new RateLimitDecision(false, retryAfterSeconds);
        }
    }
}
