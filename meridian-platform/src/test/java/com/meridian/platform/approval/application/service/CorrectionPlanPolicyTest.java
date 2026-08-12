package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrectionPlanPolicyTest {

    private final CorrectionPlanPolicy policy = new CorrectionPlanPolicy();

    @Test
    void acceptsExactCustomerSupportingUploadContract() {
        CorrectionTaskRequest task = new CorrectionTaskRequest(
                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                CorrectionResponsibility.CUSTOMER,
                DocumentType.RECENT_PAYSLIP,
                true,
                null,
                null,
                "Upload a recent payslip.",
                null
        );

        assertDoesNotThrow(() -> policy.validateCustomerRevision(new CorrectionPlanRequest(List.of(task))));
    }

    @Test
    void rejectsEmptyOversizedAndDuplicatePlans() {
        assertInvalid(new CorrectionPlanRequest(List.of()));
        assertInvalid(new CorrectionPlanRequest(java.util.Collections.nCopies(11, supportingUpload())));
        assertInvalid(new CorrectionPlanRequest(List.of(supportingUpload(), supportingUpload())));
    }

    @Test
    void rejectsApplicationTermsAndMalformedReplacementContracts() {
        UUID itemId = UUID.randomUUID();
        UUID baselineId = UUID.randomUUID();
        assertInvalid(new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REPLACEMENT,
                CorrectionResponsibility.CUSTOMER,
                DocumentType.RECENT_PAYSLIP,
                false,
                itemId,
                null,
                "Replace the document.",
                null
        ))));
        assertInvalid(new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REPLACEMENT,
                CorrectionResponsibility.STAFF,
                DocumentType.RECENT_PAYSLIP,
                false,
                itemId,
                baselineId,
                null,
                "Replace the document."
        ))));
    }

    @Test
    void rejectsStaffWorkInCustomerRevisionAndInstructionLeakage() {
        assertInvalidCustomer(new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REVIEW,
                CorrectionResponsibility.STAFF,
                DocumentType.RECENT_PAYSLIP,
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Review the current evidence."
        ))));
        assertInvalid(new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                CorrectionResponsibility.CUSTOMER,
                DocumentType.RECENT_PAYSLIP,
                true,
                null,
                null,
                "Customer instruction",
                "Staff-only instruction"
        ))));
        assertInvalid(new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REVIEW,
                CorrectionResponsibility.STAFF,
                DocumentType.INCOME_PROOF,
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Customer-only instruction",
                null
        ))));
    }

    @Test
    void rejectsFinancialCorrectionWithStableFieldError() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validate(new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                        CorrectionScope.APPLICATION_TERMS,
                        CorrectionResponsibility.CUSTOMER,
                        null, false, null, null, "Change the amount.", null
                ))))
        );
        assertEquals("CORRECTION_FIELD_NOT_ALLOWED", exception.getErrorCode());
    }

    @Test
    void acceptsStaffSupportingUploadAndExactMixedComposition() {
        CorrectionTaskRequest staffUpload = new CorrectionTaskRequest(
                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                CorrectionResponsibility.STAFF,
                DocumentType.RECENT_PAYSLIP,
                true, null, null, null, "Upload the requested payslip."
        );
        assertDoesNotThrow(() ->
                policy.validateStaffCorrection(new CorrectionPlanRequest(List.of(staffUpload))));

        CorrectionTaskRequest customerUpload = supportingUpload();
        CorrectionTaskRequest staffReview = new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REVIEW,
                CorrectionResponsibility.STAFF,
                DocumentType.RECENT_PAYSLIP,
                false, UUID.randomUUID(), UUID.randomUUID(),
                null, "Review the current payslip."
        );
        assertDoesNotThrow(() -> policy.validateMixedCorrection(
                new CorrectionPlanRequest(List.of(customerUpload, staffReview))));
    }

    @Test
    void rejectsSingleActorMixedPlanAndDuplicateChecklistCreation() {
        BusinessRuleViolationException singleActor = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateMixedCorrection(
                        new CorrectionPlanRequest(List.of(supportingUpload())))
        );
        assertEquals("INVALID_CORRECTION_PLAN", singleActor.getErrorCode());

        CorrectionTaskRequest staffUpload = new CorrectionTaskRequest(
                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                CorrectionResponsibility.STAFF,
                DocumentType.RECENT_PAYSLIP,
                true, null, null, null, "Upload the requested payslip."
        );
        assertInvalid(new CorrectionPlanRequest(List.of(supportingUpload(), staffUpload)));
    }

    private CorrectionTaskRequest supportingUpload() {
        return new CorrectionTaskRequest(
                CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                CorrectionResponsibility.CUSTOMER,
                DocumentType.RECENT_PAYSLIP,
                true,
                null,
                null,
                "Upload a recent payslip.",
                null
        );
    }

    private void assertInvalid(CorrectionPlanRequest plan) {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validate(plan)
        );
        assertEquals("INVALID_CORRECTION_PLAN", exception.getErrorCode());
    }

    private void assertInvalidCustomer(CorrectionPlanRequest plan) {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateCustomerRevision(plan)
        );
        assertEquals("INVALID_CORRECTION_PLAN", exception.getErrorCode());
    }
}
