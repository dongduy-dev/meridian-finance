package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepaymentInstallmentProgressTest {

    @Test
    void initializesSeparatelyWithoutMutatingScheduleObligation() {
        RepaymentSchedule schedule = schedule(LocalDate.of(2026, 8, 27));
        RepaymentScheduleItem item = schedule.items().getFirst();

        RepaymentInstallmentProgress progress =
                RepaymentInstallmentProgress.initial(
                        schedule,
                        item,
                        LocalDate.of(2026, 7, 27),
                        LocalDateTime.of(2026, 7, 27, 10, 0)
                );

        assertEquals(money("1000"), item.principalDue());
        assertEquals(money("1000"), progress.principalOutstanding());
        assertEquals(money("0"), progress.totalPaid());
        assertEquals(RepaymentInstallmentStatus.NOT_DUE, progress.status());
        progress.validateAgainst(item);
    }

    @Test
    void derivesDueAndOverdueInitialStateFromExplicitEvaluationDate() {
        RepaymentSchedule schedule = schedule(LocalDate.of(2026, 8, 27));
        RepaymentScheduleItem item = schedule.items().getFirst();

        assertEquals(
                RepaymentInstallmentStatus.DUE,
                RepaymentInstallmentProgress.initial(
                        schedule,
                        item,
                        item.dueDate(),
                        LocalDateTime.of(2026, 8, 27, 10, 0)
                ).status()
        );
        assertEquals(
                RepaymentInstallmentStatus.OVERDUE,
                RepaymentInstallmentProgress.initial(
                        schedule,
                        item,
                        item.dueDate().plusDays(1),
                        LocalDateTime.of(2026, 8, 28, 10, 0)
                ).status()
        );
    }

    @Test
    void rejectsNegativeOrScheduleMismatchedProgress() {
        RepaymentSchedule schedule = schedule(LocalDate.of(2026, 8, 27));
        RepaymentInstallmentProgress progress =
                RepaymentInstallmentProgress.initial(
                        schedule,
                        schedule.items().getFirst(),
                        LocalDate.of(2026, 7, 27),
                        LocalDateTime.of(2026, 7, 27, 10, 0)
                );
        assertThrows(BusinessRuleViolationException.class, () ->
                progress.validateAgainst(new RepaymentScheduleItem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        LocalDate.of(2026, 8, 27),
                        money("1000"),
                        money("100"),
                        money("0"),
                        money("1100")
                )));
    }

    private static RepaymentSchedule schedule(LocalDate dueDate) {
        UUID itemId = UUID.randomUUID();
        return new RepaymentSchedule(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                RepaymentScheduleType.FINAL,
                1,
                1,
                money("1000"),
                money("100"),
                money("0"),
                money("1100"),
                dueDate,
                dueDate,
                LocalDateTime.of(2026, 7, 27, 10, 0),
                List.of(new RepaymentScheduleItem(
                        itemId,
                        UUID.randomUUID(),
                        1,
                        dueDate,
                        money("1000"),
                        money("100"),
                        money("0"),
                        money("1100")
                ))
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
