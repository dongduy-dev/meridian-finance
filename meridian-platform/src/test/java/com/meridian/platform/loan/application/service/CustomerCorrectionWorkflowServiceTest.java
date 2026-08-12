package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.approval.domain.model.CorrectionResponsibility;
import com.meridian.platform.approval.domain.model.CorrectionScope;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerCorrectionWorkflowServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ITEM_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID VERSION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 9, 0);

    private LoanCorrectionRepository corrections;
    private LoanReviewCycleRepository reviewCycles;
    private LoanDocumentChecklistPort documents;
    private BusinessAuditPublisher audits;
    private CustomerCorrectionWorkflowService service;

    @BeforeEach
    void setUp() {
        corrections = mock(LoanCorrectionRepository.class);
        reviewCycles = mock(LoanReviewCycleRepository.class);
        documents = mock(LoanDocumentChecklistPort.class);
        audits = mock(BusinessAuditPublisher.class);
        when(corrections.findActiveRequestByApplicationIdForUpdate(APPLICATION_ID))
                .thenReturn(Optional.empty());
        when(corrections.saveRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(corrections.saveTask(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.requireCurrentVersionSnapshot(APPLICATION_ID, ITEM_ID, VERSION_ID))
                .thenReturn(new LoanDocumentChecklistPort.CurrentDocumentVersionSnapshot(
                        DocumentType.INCOME_PROOF,
                        VERSION_ID
                ));
        service = new CustomerCorrectionWorkflowService(
                corrections,
                reviewCycles,
                documents,
                audits
        );
    }

    @Test
    void verificationOriginatedUclReplacementUsesSharedCorrectionEvidence() {
        LoanCorrectionRequest result = service.createFromProductVerification(
                application(ProductCode.UNSECURED_CONSUMER_LOAN),
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                replacementPlan(DocumentType.INCOME_PROOF),
                operation()
        );

        assertEquals("COMPLETE_PRODUCT_VERIFICATION", result.sourceAction());
        assertEquals(LoanCorrectionRequestStatus.OPEN, result.status());
        assertNull(result.sourceReviewCycleId());
        ArgumentCaptor<LoanCorrectionTask> task = ArgumentCaptor.forClass(LoanCorrectionTask.class);
        verify(corrections).saveTask(task.capture());
        assertEquals(DocumentType.INCOME_PROOF, task.getValue().documentType());
        assertEquals(ITEM_ID, task.getValue().checklistItemId());
        verify(reviewCycles, never()).save(any());
        verify(audits).publish(any());
    }

    @Test
    void uclRejectsRecentPayslipAndSalaryRejectsUclOnlyEvidence() {
        assertInvalid(() -> service.createFromProductVerification(
                application(ProductCode.UNSECURED_CONSUMER_LOAN),
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                replacementPlan(DocumentType.RECENT_PAYSLIP),
                operation()
        ));
        assertInvalid(() -> service.createFromProductVerification(
                application(ProductCode.SALARY_ADVANCE),
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                replacementPlan(DocumentType.INCOME_PROOF),
                operation()
        ));
        verify(corrections, never()).saveRequest(any());
    }

    @Test
    void uclRejectsMismatchedChecklistDocumentType() {
        when(documents.requireCurrentVersionSnapshot(APPLICATION_ID, ITEM_ID, VERSION_ID))
                .thenReturn(new LoanDocumentChecklistPort.CurrentDocumentVersionSnapshot(
                        DocumentType.BANK_STATEMENT,
                        VERSION_ID
                ));

        assertInvalid(() -> service.createFromProductVerification(
                application(ProductCode.UNSECURED_CONSUMER_LOAN),
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                replacementPlan(DocumentType.INCOME_PROOF),
                operation()
        ));
        verify(corrections, never()).saveRequest(any());
    }

    @Test
    void uclRejectsForeignChecklistItemAndStaleBaselineBeforeCreatingCorrectionEvidence() {
        when(documents.requireCurrentVersionSnapshot(APPLICATION_ID, ITEM_ID, VERSION_ID))
                .thenThrow(new BusinessStateConflictException(
                        "DOCUMENT_CHECKLIST_ITEM_MISMATCH",
                        "Document checklist item does not belong to the Loan Application."
                ))
                .thenThrow(new BusinessStateConflictException(
                        "STALE_DOCUMENT_VERSION",
                        "The expected document version is no longer current."
                ));

        BusinessStateConflictException foreign = assertThrows(
                BusinessStateConflictException.class,
                () -> service.createFromProductVerification(
                        application(ProductCode.UNSECURED_CONSUMER_LOAN),
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        replacementPlan(DocumentType.INCOME_PROOF),
                        operation()
                )
        );
        assertEquals("DOCUMENT_CHECKLIST_ITEM_MISMATCH", foreign.getErrorCode());

        BusinessStateConflictException stale = assertThrows(
                BusinessStateConflictException.class,
                () -> service.createFromProductVerification(
                        application(ProductCode.UNSECURED_CONSUMER_LOAN),
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        replacementPlan(DocumentType.INCOME_PROOF),
                        operation()
                )
        );
        assertEquals("STALE_DOCUMENT_VERSION", stale.getErrorCode());
        verify(corrections, never()).saveRequest(any());
    }

    @Test
    void uclRejectsSupportingCreationAndApplicationTerms() {
        CorrectionPlanRequest supportingCreation = new CorrectionPlanRequest(List.of(
                new CorrectionTaskRequest(
                        CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                        CorrectionResponsibility.CUSTOMER,
                        DocumentType.BANK_STATEMENT,
                        true,
                        null,
                        null,
                        "Upload another bank statement.",
                        null
                )
        ));
        assertInvalid(() -> service.createFromProductVerification(
                application(ProductCode.UNSECURED_CONSUMER_LOAN),
                CorrectionReasonCode.SUPPORTING_DOCUMENT_REQUIRED,
                supportingCreation,
                operation()
        ));

        CorrectionPlanRequest terms = new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.APPLICATION_TERMS,
                CorrectionResponsibility.CUSTOMER,
                null,
                false,
                null,
                null,
                "Change the requested amount.",
                null
        )));
        BusinessRuleViolationException error = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.createFromProductVerification(
                        application(ProductCode.UNSECURED_CONSUMER_LOAN),
                        CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                        terms,
                        operation()
                )
        );
        assertEquals("CORRECTION_FIELD_NOT_ALLOWED", error.getErrorCode());
    }

    @Test
    void collateralCorrectionRemainsUnsupported() {
        assertInvalid(() -> service.createFromProductVerification(
                application(ProductCode.COLLATERAL_LOAN),
                CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                replacementPlan(DocumentType.INCOME_PROOF),
                operation()
        ));
    }

    private void assertInvalid(Runnable operation) {
        BusinessRuleViolationException error = assertThrows(
                BusinessRuleViolationException.class,
                operation::run
        );
        assertEquals("INVALID_CORRECTION_PLAN", error.getErrorCode());
    }

    private CorrectionPlanRequest replacementPlan(DocumentType documentType) {
        return new CorrectionPlanRequest(List.of(new CorrectionTaskRequest(
                CorrectionScope.DOCUMENT_REPLACEMENT,
                CorrectionResponsibility.CUSTOMER,
                documentType,
                false,
                ITEM_ID,
                VERSION_ID,
                "Replace the document.",
                null
        )));
    }

    private LoanApplication application(ProductCode productCode) {
        ProductType productType = switch (productCode) {
            case SALARY_ADVANCE -> ProductType.SALARY_BASED;
            case UNSECURED_CONSUMER_LOAN -> ProductType.UNSECURED;
            case COLLATERAL_LOAN -> ProductType.SECURED;
        };
        return new LoanApplication(
                APPLICATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                productCode.name() + "-000001",
                productCode,
                productType,
                LoanApplicationStatus.VERIFICATION_PENDING,
                new BigDecimal("5000000.00"),
                productCode == ProductCode.SALARY_ADVANCE ? 1 : 6,
                NOW.minusDays(1)
        );
    }

    private BusinessOperationContext operation() {
        return BusinessOperationContext.user(UUID.randomUUID(), ACTOR_ID, NOW);
    }
}
