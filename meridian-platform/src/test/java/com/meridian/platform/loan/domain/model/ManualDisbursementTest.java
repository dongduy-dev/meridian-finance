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

class ManualDisbursementTest {

    @Test
    void createsImmutableEvidenceFromContractAndCanonicalizesReference() {
        LoanContract contract = LoanContractTestData.ready();
        LoanAccount account = LoanAccount.activate(UUID.randomUUID(), contract, LocalDateTime.now());

        ManualDisbursement evidence = ManualDisbursement.confirmed(
                UUID.randomUUID(),
                contract,
                account,
                UUID.randomUUID(),
                contract.contractVersion(),
                "  bank:transfer-001  ",
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 7, 27, 10, 0)
        );

        assertEquals("BANK:TRANSFER-001", evidence.externalTransferReference());
        assertEquals(contract.financialTerms().approvedPrincipal(), evidence.disbursedAmount());
        assertEquals(contract.id(), evidence.loanContractId());
        assertEquals(account.id(), evidence.loanAccountId());
    }

    @Test
    void rejectsInvalidReferenceAmountDatesAndVersion() {
        LoanContract contract = LoanContractTestData.ready();
        LoanAccount account = LoanAccount.activate(UUID.randomUUID(), contract, LocalDateTime.now());

        assertThrows(BusinessRuleViolationException.class, () -> direct(
                contract,
                account,
                1,
                "bad reference",
                money(1_000),
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> direct(
                contract,
                account,
                1,
                "BANK-1",
                new BigDecimal("1000.50"),
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> direct(
                contract,
                account,
                1,
                "BANK-1",
                money(1_000),
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 7, 27)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> direct(
                contract,
                account,
                1,
                "BANK-1",
                money(1_000),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 3, 1)
        ));
        assertThrows(BusinessRuleViolationException.class, () -> direct(
                contract,
                account,
                0,
                "BANK-1",
                money(1_000),
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27)
        ));
        assertThrows(BusinessStateConflictException.class, () -> ManualDisbursement.confirmed(
                UUID.randomUUID(),
                contract,
                account,
                UUID.randomUUID(),
                2,
                "BANK-1",
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27),
                UUID.randomUUID(),
                LocalDateTime.now()
        ));
    }

    @Test
    void toStringRedactsTransferReferenceAndAmount() {
        LoanContract contract = LoanContractTestData.ready();
        LoanAccount account = LoanAccount.activate(UUID.randomUUID(), contract, LocalDateTime.now());
        ManualDisbursement evidence = ManualDisbursement.confirmed(
                UUID.randomUUID(),
                contract,
                account,
                UUID.randomUUID(),
                1,
                "SECRET-TRANSFER-1",
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 27),
                UUID.randomUUID(),
                LocalDateTime.now()
        );

        assertTrue(evidence.toString().contains("transferEvidence=redacted"));
        assertFalse(evidence.toString().contains("SECRET-TRANSFER-1"));
        assertFalse(evidence.toString().contains("1000.00"));
    }

    private static ManualDisbursement direct(
            LoanContract contract,
            LoanAccount account,
            int version,
            String reference,
            BigDecimal amount,
            LocalDate valueDate,
            LocalDate firstRepaymentDate
    ) {
        return new ManualDisbursement(
                UUID.randomUUID(),
                contract.loanApplicationId(),
                contract.id(),
                account.id(),
                UUID.randomUUID(),
                version,
                reference,
                amount,
                valueDate,
                firstRepaymentDate,
                UUID.randomUUID(),
                LocalDateTime.now()
        );
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }
}
