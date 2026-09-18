package com.meridian.platform.document.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntakeEvidenceTypeTest {
    @Test
    void identityEvidenceWorksForBothProductsAndFormsRemainProductSpecific() {
        assertDoesNotThrow(() -> IntakeEvidenceType.CUSTOMER_IDENTITY.requireProduct("UNSECURED_CONSUMER_LOAN"));
        assertDoesNotThrow(() -> IntakeEvidenceType.CUSTOMER_IDENTITY.requireProduct("COLLATERAL_LOAN"));
        assertDoesNotThrow(() -> IntakeEvidenceType.UCL_PAPER_APPLICATION.requireProduct("UNSECURED_CONSUMER_LOAN"));
        assertDoesNotThrow(() -> IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION.requireProduct("COLLATERAL_LOAN"));
        assertThrows(BusinessRuleViolationException.class,
                () -> IntakeEvidenceType.UCL_PAPER_APPLICATION.requireProduct("COLLATERAL_LOAN"));
        assertThrows(BusinessRuleViolationException.class,
                () -> IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION.requireProduct("UNSECURED_CONSUMER_LOAN"));
    }
}
