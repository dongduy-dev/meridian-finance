package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractRepaymentItem;
import com.meridian.platform.loan.domain.model.ProtectedDisbursementBankAccount;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinalRepaymentScheduleGeneratorTest {

    private final FinalRepaymentScheduleGenerator generator = new FinalRepaymentScheduleGenerator();

    @Test
    void generatesOneTwoAndThreeMonthSchedules() {
        for (int term = 1; term <= 3; term++) {
            LoanContract contract = readyContract(term);
            RepaymentSchedule schedule = generate(
                    contract,
                    LocalDate.of(2026, 7, 26),
                    LocalDate.of(2026, 7, 27)
            );

            assertEquals(term, schedule.items().size());
            assertEquals(term, schedule.approvedTermMonths());
            assertEquals(contract.financialTerms().approvedPrincipal(), schedule.approvedPrincipal());
            assertEquals(contract.financialTerms().totalInterest(), schedule.totalInterest());
            assertEquals(contract.financialTerms().feeAmount(), schedule.feeAmount());
            assertEquals(contract.financialTerms().totalRepaymentAmount(), schedule.totalRepaymentAmount());
        }
    }

    @Test
    void preservesAndRestoresTwentyEightTwentyNineThirtyAndThirtyOneDayAnchors() {
        assertDates(
                LocalDate.of(2026, 1, 28),
                List.of(
                        LocalDate.of(2026, 1, 28),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 28)
                )
        );
        assertDates(
                LocalDate.of(2024, 1, 29),
                List.of(
                        LocalDate.of(2024, 1, 29),
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2024, 3, 29)
                )
        );
        assertDates(
                LocalDate.of(2026, 1, 29),
                List.of(
                        LocalDate.of(2026, 1, 29),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 29)
                )
        );
        assertDates(
                LocalDate.of(2026, 1, 30),
                List.of(
                        LocalDate.of(2026, 1, 30),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 30)
                )
        );
        assertDates(
                LocalDate.of(2024, 1, 30),
                List.of(
                        LocalDate.of(2024, 1, 30),
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2024, 3, 30)
                )
        );
        assertDates(
                LocalDate.of(2026, 1, 31),
                List.of(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31)
                )
        );
        assertDates(
                LocalDate.of(2024, 1, 31),
                List.of(
                        LocalDate.of(2024, 1, 31),
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2024, 3, 31)
                )
        );
    }

    @Test
    void monthlyInstallmentUclScheduleClampsFebruaryAndRestoresTheAnchor() {
        LoanContract contract = readyContract(3, RepaymentMethod.MONTHLY_INSTALLMENT);

        RepaymentSchedule schedule = generate(
                contract,
                LocalDate.of(2026, 1, 30),
                LocalDate.of(2026, 1, 31)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31)
                ),
                schedule.items().stream().map(item -> item.dueDate()).toList()
        );
        assertEquals(RepaymentMethod.MONTHLY_INSTALLMENT,
                contract.financialTerms().repaymentMethod());
    }

    @Test
    void enforcesOneCalendarMonthFirstRepaymentBoundary() {
        LoanContract contract = readyContract(1);

        generate(contract, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28));
        assertThrows(BusinessRuleViolationException.class, () -> generate(
                contract,
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 31)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> generate(
                contract,
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 3, 1)
        ));
    }

    @Test
    void copiesEveryContractItemAmountAndSourceWithoutRepricing() {
        LoanContract contract = readyContract(3);
        RepaymentSchedule schedule = generate(
                contract,
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 27)
        );

        for (int index = 0; index < contract.repaymentItems().size(); index++) {
            LoanContractRepaymentItem source = contract.repaymentItems().get(index);
            var generated = schedule.items().get(index);
            assertEquals(source.id(), generated.sourceLoanContractRepaymentItemId());
            assertEquals(source.installmentNumber(), generated.installmentNumber());
            assertEquals(source.principalDue(), generated.principalDue());
            assertEquals(source.interestDue(), generated.interestDue());
            assertEquals(source.feeDue(), generated.feeDue());
            assertEquals(source.totalDue(), generated.totalDue());
        }
    }

    private void assertDates(LocalDate firstDate, List<LocalDate> expected) {
        RepaymentSchedule schedule = generate(
                readyContract(3),
                firstDate.minusDays(1),
                firstDate
        );
        assertEquals(expected, schedule.items().stream().map(item -> item.dueDate()).toList());
    }

    private RepaymentSchedule generate(
            LoanContract contract,
            LocalDate valueDate,
            LocalDate firstRepaymentDate
    ) {
        LoanAccount account = LoanAccount.activate(UUID.randomUUID(), contract, LocalDateTime.now());
        List<UUID> itemIds = contract.repaymentItems().stream().map(ignored -> UUID.randomUUID()).toList();
        return generator.generate(
                UUID.randomUUID(),
                itemIds,
                contract,
                account,
                valueDate,
                firstRepaymentDate,
                LocalDateTime.of(2026, 7, 27, 10, 0)
        );
    }

    private static LoanContract readyContract(int term) {
        return readyContract(term, RepaymentMethod.ON_SALARY_DATE);
    }

    private static LoanContract readyContract(int term, RepaymentMethod repaymentMethod) {
        List<LoanContractRepaymentItem> items = IntStream.rangeClosed(1, term)
                .mapToObj(installment -> new LoanContractRepaymentItem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        installment,
                        money(1_000),
                        money(100),
                        money(0),
                        money(1_100)
                ))
                .toList();
        ApprovedOfferFinancialTerms terms = new ApprovedOfferFinancialTerms(
                money(1_000L * term),
                term,
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.100000"),
                money(100L * term),
                money(0),
                money(1_100L * term),
                repaymentMethod
        );
        LocalDateTime preparedAt = LocalDateTime.of(2026, 7, 26, 8, 0);
        LoanContract prepared = LoanContract.prepared(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MCT-" + UUID.randomUUID(),
                1,
                terms,
                items,
                new ProtectedDisbursementBankAccount(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "VCB",
                        "Vietcombank",
                        "MERIDIAN CUSTOMER",
                        "7890",
                        true,
                        true,
                        preparedAt,
                        "AES-256-GCM",
                        "v1",
                        new byte[12],
                        new byte[]{1},
                        "DISBURSEMENT_ACCOUNT_V1"
                ),
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                preparedAt,
                null
        );
        LoanContract acknowledged = prepared.acknowledge(
                UUID.randomUUID(),
                UUID.randomUUID(),
                preparedAt.plusMinutes(1)
        );
        return acknowledged.confirmReady(
                UUID.randomUUID(),
                UUID.randomUUID(),
                preparedAt.plusMinutes(2)
        );
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
