package com.meridian.platform.document.application.service;

import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDocumentChecklistResolverTest {

    private final SalaryAdvanceDocumentChecklistResolver salaryResolver =
            new SalaryAdvanceDocumentChecklistResolver();
    private final UnsecuredConsumerLoanDocumentChecklistResolver uclResolver =
            new UnsecuredConsumerLoanDocumentChecklistResolver();
    private final CollateralLoanDocumentChecklistResolver collateralResolver =
            new CollateralLoanDocumentChecklistResolver();

    @Test
    void resolvesThreeRequiredUclEvidenceCategories() {
        List<DocumentChecklistItem> items = uclResolver.resolve(
                UUID.randomUUID(),
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LocalDateTime.parse("2026-08-11T09:00:00")
        );

        assertEquals(
                List.of(DocumentType.INCOME_PROOF, DocumentType.BANK_STATEMENT, DocumentType.EMPLOYMENT_PROOF),
                items.stream().map(DocumentChecklistItem::documentType).toList()
        );
        assertTrue(items.stream().allMatch(
                item -> item.requirementStatus() == DocumentRequirementStatus.REQUIRED
        ));
    }

    @Test
    void preservesEmptySalaryAdvanceSubmissionChecklist() {
        assertTrue(salaryResolver.resolve(
                UUID.randomUUID(),
                ProductCode.SALARY_ADVANCE,
                LocalDateTime.MIN
        ).isEmpty());
    }

    @Test
    void resolvesOneRequiredCollateralOwnershipEvidenceItem() {
        List<DocumentChecklistItem> items = collateralResolver.resolve(
                UUID.randomUUID(), ProductCode.COLLATERAL_LOAN, LocalDateTime.MIN
        );

        assertEquals(1, items.size());
        assertEquals(DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE, items.getFirst().documentType());
        assertEquals(DocumentRequirementStatus.REQUIRED, items.getFirst().requirementStatus());
    }

    @Test
    void resolversRejectUnsupportedProducts() {
        assertThrows(IllegalArgumentException.class, () -> salaryResolver.resolve(
                UUID.randomUUID(), ProductCode.UNSECURED_CONSUMER_LOAN, LocalDateTime.MIN
        ));
        assertThrows(IllegalArgumentException.class, () -> uclResolver.resolve(
                UUID.randomUUID(), ProductCode.COLLATERAL_LOAN, LocalDateTime.MIN
        ));
        assertThrows(IllegalArgumentException.class, () -> collateralResolver.resolve(
                UUID.randomUUID(), ProductCode.UNSECURED_CONSUMER_LOAN, LocalDateTime.MIN
        ));
    }
}
