package com.meridian.platform.partner.domain.service;

import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerEmployeeVerificationPolicyTest {

    private final PartnerEmployeeVerificationPolicy policy = new PartnerEmployeeVerificationPolicy();

    @Test
    void allowsActivePartnerCompanyForEligibility() {
        assertDoesNotThrow(() -> policy.validatePartnerCompanyCanBeUsedForEligibility(
                partnerCompany(PartnerCompanyStatus.ACTIVE)
        ));
    }

    @Test
    void rejectsInactivePartnerCompanyForEligibility() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validatePartnerCompanyCanBeUsedForEligibility(partnerCompany(PartnerCompanyStatus.INACTIVE))
        );

        assertEquals("PARTNER_COMPANY_INACTIVE", exception.getErrorCode());
    }

    @Test
    void returnsMatchedActiveForSingleActiveEmployee() {
        EmployeeVerificationOutcome outcome = policy.determineOutcome(List.of(employee(true, PartnerEmployeeStatus.ACTIVE)));

        assertEquals(EmployeeVerificationOutcome.MATCHED_ACTIVE, outcome);
        assertFalse(policy.requiresManualReview(outcome));
    }

    @Test
    void returnsMatchedInactiveForSingleInactiveEmployee() {
        EmployeeVerificationOutcome outcome = policy.determineOutcome(List.of(employee(false, PartnerEmployeeStatus.SUSPENDED)));

        assertEquals(EmployeeVerificationOutcome.MATCHED_INACTIVE, outcome);
        assertFalse(policy.requiresManualReview(outcome));
    }

    @Test
    void returnsNotFoundForNoMatches() {
        EmployeeVerificationOutcome outcome = policy.determineOutcome(List.of());

        assertEquals(EmployeeVerificationOutcome.NOT_FOUND, outcome);
        assertTrue(policy.requiresManualReview(outcome));
    }

    @Test
    void returnsMultipleMatchesForAmbiguousMatches() {
        EmployeeVerificationOutcome outcome = policy.determineOutcome(List.of(
                employee(true, PartnerEmployeeStatus.ACTIVE),
                employee(true, PartnerEmployeeStatus.ACTIVE)
        ));

        assertEquals(EmployeeVerificationOutcome.MULTIPLE_MATCHES, outcome);
        assertTrue(policy.requiresManualReview(outcome));
    }

    private PartnerCompany partnerCompany(PartnerCompanyStatus status) {
        return new PartnerCompany(
                UUID.randomUUID(),
                "MERIDIAN_PARTNER",
                "Meridian Partner Co.",
                status,
                BigDecimal.valueOf(20_000_000)
        );
    }

    private PartnerEmployee employee(boolean active, PartnerEmployeeStatus status) {
        return new PartnerEmployee(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "EMP-001",
                "IDREF-001",
                BigDecimal.valueOf(18_000_000),
                BigDecimal.valueOf(6_000_000),
                status,
                active
        );
    }
}
