package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
