package com.meridian.platform.loan.infrastructure.adapter.in.scheduler;

import com.meridian.platform.loan.application.port.in.RunOverdueEvaluationBatchUseCase;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanAccountOverdueSchedulerTest {

    @Test
    void samplesClockOnceAndUsesUtcDateRegardlessOfJvmDefaultZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Pacific/Honolulu")));
            Instant instant = Instant.parse("2026-08-01T00:02:03Z");
            AtomicReference<RunOverdueEvaluationBatchUseCase.Command> captured =
                    new AtomicReference<>();
            RunOverdueEvaluationBatchUseCase batch = command -> {
                captured.set(command);
                return new RunOverdueEvaluationBatchUseCase.Result(0, 0, 0, 0, 0);
            };

            new LoanAccountOverdueScheduler(batch, Clock.fixed(instant, ZoneId.of("Asia/Saigon")), 37)
                    .evaluateOverdueAccounts();

            assertEquals(LocalDate.of(2026, 8, 1), captured.get().evaluationDate());
            assertEquals(LocalDateTime.of(2026, 8, 1, 0, 2, 3), captured.get().evaluatedAt());
            assertEquals(37, captured.get().batchSize());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        RunOverdueEvaluationBatchUseCase batch = command -> null;
        assertThrows(IllegalArgumentException.class,
                () -> new LoanAccountOverdueScheduler(batch, Clock.systemUTC(), 0));
    }
}
