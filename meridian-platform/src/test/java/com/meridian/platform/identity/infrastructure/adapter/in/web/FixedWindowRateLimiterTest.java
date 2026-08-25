package com.meridian.platform.identity.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWindowRateLimiterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void concurrentRequestsCannotExceedTheConfiguredMaximum() throws Exception {
        int maximum = 10;
        int requestCount = 50;
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(
                maximum,
                Duration.ofMinutes(1),
                100,
                CLOCK,
                "login"
        );
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < requestCount; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return limiter.acquire("192.0.2.1").allowed();
                }));
            }
            ready.await();
            start.countDown();

            long admitted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    admitted++;
                }
            }
            assertEquals(maximum, admitted);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void trackedClientStateRemainsBoundedByLeastRecentlyUsedEviction() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(
                1,
                Duration.ofMinutes(1),
                3,
                CLOCK,
                "login"
        );

        assertTrue(limiter.acquire("192.0.2.1").allowed());
        assertTrue(limiter.acquire("192.0.2.2").allowed());
        assertTrue(limiter.acquire("192.0.2.3").allowed());
        assertFalse(limiter.acquire("192.0.2.1").allowed());
        assertTrue(limiter.acquire("192.0.2.4").allowed());

        assertEquals(3, limiter.trackedKeyCount());
        assertTrue(limiter.acquire("192.0.2.2").allowed());
    }

    @Test
    void rejectsInvalidRequestLimitsAndWindows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(0, Duration.ofMinutes(1), 10, CLOCK, "login")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(-1, Duration.ofMinutes(1), 10, CLOCK, "refresh")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(1, Duration.ZERO, 10, CLOCK, "login")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(1, Duration.ofSeconds(-1), 10, CLOCK, "refresh")
        );
    }
}
