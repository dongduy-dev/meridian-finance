package com.meridian.platform.loan.domain.model;

import com.meridian.platform.loan.testsupport.LoanContractTestData;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
}
