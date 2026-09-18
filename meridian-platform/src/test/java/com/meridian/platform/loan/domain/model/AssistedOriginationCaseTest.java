package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssistedOriginationCaseTest {

    @Test
    void openCaseAssociatesCustomerAndAbandonmentIsTerminal() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 17, 8, 0);
        AssistedOriginationCase opened = new AssistedOriginationCase(
                UUID.randomUUID(), ProductCode.UNSECURED_CONSUMER_LOAN, null,
                AssistedOriginationCaseStatus.OPEN, UUID.randomUUID(), created, created, null);
        UUID customerId = UUID.randomUUID();

        AssistedOriginationCase abandoned = opened.associateCustomer(customerId, created.plusMinutes(1))
                .abandon(created.plusMinutes(2));

        assertEquals(customerId, abandoned.customerId());
        assertEquals(AssistedOriginationCaseStatus.ABANDONED, abandoned.status());
        assertThrows(BusinessStateConflictException.class,
                () -> abandoned.associateCustomer(UUID.randomUUID(), created.plusMinutes(3)));
        assertThrows(BusinessStateConflictException.class,
                () -> abandoned.abandon(created.plusMinutes(3)));
    }

    @Test
    void rejectsSalaryAdvance() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 8, 0);
        assertThrows(IllegalArgumentException.class, () -> new AssistedOriginationCase(
                UUID.randomUUID(), ProductCode.SALARY_ADVANCE, null,
                AssistedOriginationCaseStatus.OPEN, UUID.randomUUID(), now, now, null));
    }

    @Test
    void completionRequiresCustomerAndBindsImmutableLoanApplicationResult() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 17, 8, 0);
        AssistedOriginationCase opened = new AssistedOriginationCase(
                UUID.randomUUID(), ProductCode.UNSECURED_CONSUMER_LOAN, null,
                AssistedOriginationCaseStatus.OPEN, UUID.randomUUID(), created, created, null, null);
        assertNull(opened.loanApplicationId());
        assertThrows(BusinessStateConflictException.class,
                () -> opened.complete(UUID.randomUUID(), created.plusMinutes(1)));

        UUID loanApplicationId = UUID.randomUUID();
        AssistedOriginationCase completed = opened
                .associateCustomer(UUID.randomUUID(), created.plusMinutes(1))
                .complete(loanApplicationId, created.plusMinutes(2));
        assertEquals(AssistedOriginationCaseStatus.COMPLETED, completed.status());
        assertEquals(loanApplicationId, completed.loanApplicationId());
        assertThrows(BusinessStateConflictException.class,
                () -> completed.complete(UUID.randomUUID(), created.plusMinutes(3)));
        assertThrows(BusinessStateConflictException.class,
                () -> completed.abandon(created.plusMinutes(3)));
        assertThrows(BusinessStateConflictException.class,
                () -> completed.associateCustomer(UUID.randomUUID(), created.plusMinutes(3)));
    }

    @Test
    void terminalStateShapeRejectsInvalidApplicationLinks() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 8, 0);
        assertThrows(IllegalArgumentException.class, () -> new AssistedOriginationCase(
                UUID.randomUUID(), ProductCode.UNSECURED_CONSUMER_LOAN, UUID.randomUUID(),
                AssistedOriginationCaseStatus.OPEN, UUID.randomUUID(), now, now, null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new AssistedOriginationCase(
                UUID.randomUUID(), ProductCode.UNSECURED_CONSUMER_LOAN, UUID.randomUUID(),
                AssistedOriginationCaseStatus.ABANDONED, UUID.randomUUID(), now, now, now, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new AssistedOriginationCase(
                UUID.randomUUID(), ProductCode.UNSECURED_CONSUMER_LOAN, UUID.randomUUID(),
                AssistedOriginationCaseStatus.COMPLETED, UUID.randomUUID(), now, now, now, null));
    }
}
