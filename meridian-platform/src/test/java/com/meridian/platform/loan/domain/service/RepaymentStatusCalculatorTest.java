package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepaymentStatusCalculatorTest {

    private final RepaymentStatusCalculator calculator =
            new RepaymentStatusCalculator();

    @Test
    void derivesInstallmentStatusesFromExplicitEvaluationDate() {
        LocalDate dueDate = LocalDate.of(2026, 8, 27);

        assertEquals(RepaymentInstallmentStatus.PAID,
                calculator.installmentStatus(
                        dueDate, money("100"), money("0"), dueDate.plusDays(1)
                ));
        assertEquals(RepaymentInstallmentStatus.OVERDUE,
                calculator.installmentStatus(
                        dueDate, money("10"), money("90"), dueDate.plusDays(1)
                ));
        assertEquals(RepaymentInstallmentStatus.PARTIALLY_PAID,
                calculator.installmentStatus(
                        dueDate, money("10"), money("90"), dueDate.minusDays(1)
                ));
        assertEquals(RepaymentInstallmentStatus.DUE,
                calculator.installmentStatus(
                        dueDate, money("0"), money("100"), dueDate
                ));
        assertEquals(RepaymentInstallmentStatus.NOT_DUE,
                calculator.installmentStatus(
                        dueDate, money("0"), money("100"), dueDate.minusDays(1)
                ));
    }

    @Test
    void rollsAccountToSettledThenOverdueOtherwiseActiveAndNeverClosed() {
        assertEquals(LoanAccountStatus.SETTLED,
                calculator.loanAccountStatus(money("0"), List.of()));
        assertEquals(LoanAccountStatus.OVERDUE,
                calculator.loanAccountStatus(
                        money("100"),
                        List.of(progress(RepaymentInstallmentStatus.OVERDUE))
                ));
        assertEquals(LoanAccountStatus.ACTIVE,
                calculator.loanAccountStatus(
                        money("100"),
                        List.of(progress(RepaymentInstallmentStatus.DUE))
                ));
    }

    private static RepaymentInstallmentProgress progress(
            RepaymentInstallmentStatus status
    ) {
        return new RepaymentInstallmentProgress(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                1,
                money("0"),
                money("0"),
                money("0"),
                money("0"),
                money("100"),
                money("0"),
                money("0"),
                money("100"),
                status,
                null,
                null,
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 27).atStartOfDay()
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
