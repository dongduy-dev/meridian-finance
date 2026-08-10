package com.meridian.platform.loan.domain.model;

import com.meridian.platform.loan.testsupport.LoanContractTestData;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanAccountTest {

    @Test
    void activatesGenericAccountFromReadyContract() {
        LoanContract contract = LoanContractTestData.ready();
        UUID id = UUID.fromString("12345678-1234-1234-1234-1234567890ab");

        LoanAccount account = LoanAccount.activate(id, contract, LocalDateTime.of(2026, 7, 27, 10, 0));

        assertEquals("LA-123456781234123412341234567890AB", account.accountNumber());
        assertEquals(LoanAccountStatus.ACTIVE, account.status());
        assertEquals(contract.loanApplicationId(), account.loanApplicationId());
        assertEquals(contract.id(), account.loanContractId());
        assertEquals(contract.disbursementBankAccount().customerId(), account.customerId());
        assertEquals(contract.financialTerms().approvedPrincipal(), account.approvedPrincipal());
        assertEquals(contract.financialTerms().approvedTermMonths(), account.approvedTermMonths());
        assertEquals(contract.financialTerms().totalInterest(), account.totalInterest());
        assertEquals(contract.financialTerms().feeAmount(), account.feeAmount());
        assertEquals(contract.financialTerms().totalRepaymentAmount(), account.totalRepaymentAmount());
    }

    @Test
    void rejectsNonReadySourceContract() {
        assertThrows(
                BusinessStateConflictException.class,
                () -> LoanAccount.activate(UUID.randomUUID(), LoanContractTestData.prepared(), LocalDateTime.now())
        );
    }

    @Test
    void rejectsInvalidIdentityAndFinancialEvidenceWhileRehydratingDocumentedStatuses() {
        LoanAccount valid = LoanAccount.activate(
                UUID.fromString("12345678-1234-1234-1234-1234567890ab"),
                LoanContractTestData.ready(),
                LocalDateTime.now()
        );

        assertThrows(BusinessRuleViolationException.class, () -> copy(
                valid,
                "LA-INVALID",
                LoanAccountStatus.ACTIVE,
                money(1_000),
                1,
                money(100),
                money(0),
                money(1_100)
        ));
        LoanAccount overdue = copy(
                valid,
                valid.accountNumber(),
                LoanAccountStatus.OVERDUE,
                money(1_000),
                1,
                money(100),
                money(0),
                money(1_100)
        );
        assertEquals(LoanAccountStatus.OVERDUE, overdue.status());
        assertThrows(BusinessRuleViolationException.class, () -> copy(
                valid,
                valid.accountNumber(),
                LoanAccountStatus.ACTIVE,
                money(0),
                1,
                money(100),
                money(0),
                money(100)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> copy(
                valid,
                valid.accountNumber(),
                LoanAccountStatus.ACTIVE,
                money(1_000),
                0,
                money(100),
                money(0),
                money(1_100)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> copy(
                valid,
                valid.accountNumber(),
                LoanAccountStatus.ACTIVE,
                new BigDecimal("1000.50"),
                1,
                money(100),
                money(0),
                new BigDecimal("1100.50")
        ));
        assertThrows(BusinessRuleViolationException.class, () -> copy(
                valid,
                valid.accountNumber(),
                LoanAccountStatus.ACTIVE,
                money(1_000),
                1,
                money(100),
                money(0),
                money(1_101)
        ));
    }

    @Test
    void toStringRedactsFinancialEvidence() {
        LoanAccount account = LoanAccount.activate(
                UUID.fromString("12345678-1234-1234-1234-1234567890ab"),
                LoanContractTestData.ready(),
                LocalDateTime.now()
        );

        assertTrue(account.toString().contains("financialEvidence=redacted"));
        assertFalse(account.toString().contains("1100.00"));
    }

    @Test
    void closesOnlyAZeroOutstandingSettledAccountWithoutChangingFinancialState() {
        LoanAccount settled = settledAccount();
        LocalDateTime closedAt = settled.updatedAt().plusMinutes(1);

        LoanAccount closed = settled.closeAdministratively(closedAt);

        assertEquals(LoanAccountStatus.CLOSED, closed.status());
        assertEquals(settled.repaymentBalance(), closed.repaymentBalance());
        assertEquals(closedAt, closed.updatedAt());
    }

    @Test
    void rejectsAdministrativeClosureFromOpenStateOrAtAnEarlierTime() {
        LoanAccount active = LoanAccount.activate(
                UUID.randomUUID(),
                LoanContractTestData.ready(),
                LocalDateTime.of(2026, 7, 27, 10, 0)
        );
        LoanAccount settled = settledAccount();

        assertThrows(
                BusinessStateConflictException.class,
                () -> active.closeAdministratively(active.updatedAt())
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> settled.closeAdministratively(settled.updatedAt().minusNanos(1))
        );
    }

    @Test
    void closedAccountRequiresZeroOutstandingAndCannotComeFromServicingState() {
        LoanAccount active = LoanAccount.activate(
                UUID.randomUUID(),
                LoanContractTestData.ready(),
                LocalDateTime.of(2026, 7, 27, 10, 0)
        );

        assertThrows(BusinessRuleViolationException.class, () -> new LoanAccount(
                active.id(),
                active.loanApplicationId(),
                active.loanContractId(),
                active.customerId(),
                active.accountNumber(),
                LoanAccountStatus.CLOSED,
                active.approvedPrincipal(),
                active.approvedTermMonths(),
                active.totalInterest(),
                active.feeAmount(),
                active.totalRepaymentAmount(),
                active.activatedAt(),
                active.repaymentBalance(),
                active.updatedAt()
        ));
        assertThrows(
                BusinessStateConflictException.class,
                () -> active.withServicingState(
                        active.repaymentBalance(),
                        LoanAccountStatus.CLOSED,
                        active.updatedAt()
                )
        );
    }

    private static LoanAccount copy(
            LoanAccount source,
            String accountNumber,
            LoanAccountStatus status,
            BigDecimal principal,
            int term,
            BigDecimal interest,
            BigDecimal fee,
            BigDecimal total
    ) {
        return new LoanAccount(
                source.id(),
                source.loanApplicationId(),
                source.loanContractId(),
                source.customerId(),
                accountNumber,
                status,
                principal,
                term,
                interest,
                fee,
                total,
                source.activatedAt()
        );
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private static LoanAccount settledAccount() {
        LoanAccount active = LoanAccount.activate(
                UUID.randomUUID(),
                LoanContractTestData.ready(),
                LocalDateTime.of(2026, 7, 27, 10, 0)
        );
        LocalDate paymentDate = LocalDate.of(2026, 7, 28);
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 28, 11, 0);
        RepaymentBalance paid = new RepaymentBalance(
                active.approvedPrincipal(),
                active.totalInterest(),
                active.feeAmount(),
                active.totalRepaymentAmount(),
                money(0),
                money(0),
                money(0),
                money(0),
                paymentDate,
                paidAt,
                paymentDate
        );
        return active.withServicingState(paid, LoanAccountStatus.SETTLED, paidAt);
    }
}
