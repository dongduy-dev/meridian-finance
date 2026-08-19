package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.application.service.CorrectionPlanPolicy;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Set;

@Service
public class CustomerCorrectionWorkflowService {
    private static final Set<DocumentType> UCL_DOCUMENT_TYPES = Set.of(
            DocumentType.INCOME_PROOF,
            DocumentType.BANK_STATEMENT,
            DocumentType.EMPLOYMENT_PROOF
    );

    private final LoanCorrectionRepository correctionRepository;
    private final LoanReviewCycleRepository reviewCycleRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final BusinessAuditPublisher auditPublisher;
    private final CorrectionPlanPolicy correctionPlanPolicy = new CorrectionPlanPolicy();

    public CustomerCorrectionWorkflowService(
            LoanCorrectionRepository correctionRepository,
            LoanReviewCycleRepository reviewCycleRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            BusinessAuditPublisher auditPublisher
    ) {
        this.correctionRepository = correctionRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.auditPublisher = auditPublisher;
    }

    public LoanCorrectionRequest createFromRecommendation(
            LoanApplication application,
            LoanApplicationReviewCycle activeCycle,
            String sourceAction,
            CorrectionReasonCode reasonCode,
            CorrectionPlanRequest plan,
            BusinessOperationContext operationContext
    ) {
        return create(
                application,
                activeCycle,
                sourceAction,
                reasonCode,
                plan,
                operationContext
        );
    }

    public LoanCorrectionRequest createFromProductVerification(
            LoanApplication application,
            CorrectionReasonCode reasonCode,
            CorrectionPlanRequest plan,
            BusinessOperationContext operationContext
    ) {
        return create(
                application,
                null,
                "COMPLETE_PRODUCT_VERIFICATION",
                reasonCode,
                plan,
                operationContext
        );
    }

    private LoanCorrectionRequest create(
            LoanApplication application,
            LoanApplicationReviewCycle sourceReviewCycle,
            String sourceAction,
            CorrectionReasonCode reasonCode,
            CorrectionPlanRequest plan,
            BusinessOperationContext operationContext
    ) {
        validateProductPlan(application, reasonCode, plan);
        if (correctionRepository.findActiveRequestByApplicationIdForUpdate(application.id()).isPresent()) {
            throw new BusinessStateConflictException(
                    "ACTIVE_CORRECTION_REQUEST_EXISTS",
                    "Loan Application already has an active correction request."
            );
        }
        if (sourceReviewCycle != null) {
            reviewCycleRepository.save(sourceReviewCycle.requireCorrection(operationContext.occurredAt()));
        }
        LoanCorrectionRequest request = correctionRepository.saveRequest(new LoanCorrectionRequest(
                UUID.randomUUID(),
                application.id(),
                sourceReviewCycle == null ? null : sourceReviewCycle.id(),
                sourceAction,
                reasonCode,
                operationContext.actorUserId(),
                LoanCorrectionRequestStatus.OPEN,
                null,
                operationContext.occurredAt(),
                null,
                null
        ));

        int sequence = 1;
        for (CorrectionTaskRequest plannedTask : plan.tasks()) {
            UUID checklistItemId = plannedTask.checklistItemId();
            UUID baselineVersionId = plannedTask.baselineDocumentVersionId();
            if (plannedTask.createChecklistItem()) {
                checklistItemId = documentChecklistPort.createRequiredItem(
                        application.id(), plannedTask.documentType(), operationContext
                );
            } else {
                documentChecklistPort.requireCurrentVersion(
                        application.id(), checklistItemId, baselineVersionId
                );
            }
            correctionRepository.saveTask(new LoanCorrectionTask(
                    UUID.randomUUID(),
                    request.id(),
                    sequence++,
                    LoanCorrectionResponsibility.valueOf(plannedTask.responsibleParty().name()),
                    LoanCorrectionScope.valueOf(plannedTask.scope().name()),
                    plannedTask.documentType(),
                    plannedTask.createChecklistItem(),
                    checklistItemId,
                    baselineVersionId,
                    normalize(plannedTask.customerInstruction()),
                    normalize(plannedTask.staffInstruction()),
                    LoanCorrectionTaskStatus.OPEN,
                    null,
                    null,
                    null,
                    operationContext.occurredAt()
            ));
        }

        BusinessAuditPayload.Builder auditPayload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, application.id())
                .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, request.id())
                .put(BusinessAuditPayloadKey.CORRECTION_REASON_CODE, reasonCode);
        if (sourceReviewCycle != null) {
            auditPayload.put(BusinessAuditPayloadKey.REVIEW_CYCLE_ID, sourceReviewCycle.id());
        }
        auditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.CORRECTION_REQUEST_CREATED,
                        BusinessAuditEntityType.LOAN_CORRECTION_REQUEST,
                        request.id(),
                        auditPayload.build()
                )
        ));
        return request;
    }

    private void validateProductPlan(
            LoanApplication application,
            CorrectionReasonCode reasonCode,
            CorrectionPlanRequest plan
    ) {
        if (reasonCode == null) {
            throw invalidPlan("A controlled correction reason is required.");
        }
        correctionPlanPolicy.validate(plan);
        switch (application.productCode()) {
            case SALARY_ADVANCE -> validateSalaryAdvancePlan(plan);
            case UNSECURED_CONSUMER_LOAN -> validateUnsecuredConsumerLoanPlan(
                    application,
                    reasonCode,
                    plan
            );
            case COLLATERAL_LOAN -> validateCollateralLoanPlan(
                    application,
                    reasonCode,
                    plan
            );
        }
    }

    private void validateSalaryAdvancePlan(CorrectionPlanRequest plan) {
        if (plan.tasks().stream().anyMatch(
                task -> task.documentType() != DocumentType.RECENT_PAYSLIP
        )) {
            throw invalidPlan("Salary Advance correction tasks require RECENT_PAYSLIP evidence.");
        }
    }

    private void validateUnsecuredConsumerLoanPlan(
            LoanApplication application,
            CorrectionReasonCode reasonCode,
            CorrectionPlanRequest plan
    ) {
        if (reasonCode == CorrectionReasonCode.RECENT_PAYSLIP_REQUIRED) {
            throw invalidPlan("RECENT_PAYSLIP is not valid Unsecured Consumer Loan evidence.");
        }
        for (CorrectionTaskRequest task : plan.tasks()) {
            if (!UCL_DOCUMENT_TYPES.contains(task.documentType())
                    || task.scope()
                    == com.meridian.platform.approval.domain.model.CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD) {
                throw invalidPlan(
                        "Unsecured Consumer Loan corrections require replacement or review of existing base evidence."
                );
            }
            LoanDocumentChecklistPort.CurrentDocumentVersionSnapshot document =
                    documentChecklistPort.requireCurrentVersionSnapshot(
                            application.id(),
                            task.checklistItemId(),
                            task.baselineDocumentVersionId()
                    );
            if (document.documentType() != task.documentType()) {
                throw invalidPlan("Correction document type does not match the checklist item.");
            }
        }
    }

    private void validateCollateralLoanPlan(
            LoanApplication application,
            CorrectionReasonCode reasonCode,
            CorrectionPlanRequest plan
    ) {
        if (reasonCode != CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED
                && reasonCode != CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED) {
            throw invalidPlan(
                    "Collateral Loan correction requires a document replacement or review reason."
            );
        }
        UUID checklistItemId = plan.tasks().getFirst().checklistItemId();
        for (CorrectionTaskRequest task : plan.tasks()) {
            if (task.documentType() != DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE
                    || task.scope()
                    == com.meridian.platform.approval.domain.model.CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD
                    || task.createChecklistItem()
                    || !checklistItemId.equals(task.checklistItemId())) {
                throw invalidPlan(
                        "Collateral Loan corrections require replacement or review of the existing ownership evidence."
                );
            }
            LoanDocumentChecklistPort.CurrentDocumentVersionSnapshot document =
                    documentChecklistPort.requireCurrentVersionSnapshot(
                            application.id(),
                            task.checklistItemId(),
                            task.baselineDocumentVersionId()
                    );
            if (document.documentType() != DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE) {
                throw invalidPlan("Correction document type does not match the ownership checklist item.");
            }
        }
    }

    private BusinessRuleViolationException invalidPlan(String message) {
        return new BusinessRuleViolationException("INVALID_CORRECTION_PLAN", message);
    }

    public LoanCorrectionRequest createFromDecision(
            LoanApplication application,
            LoanApplicationReviewCycle activeCycle,
            String sourceAction,
            CorrectionReasonCode reasonCode,
            CorrectionPlanRequest plan,
            BusinessOperationContext operationContext
    ) {
        return createFromRecommendation(
                application, activeCycle, sourceAction, reasonCode, plan, operationContext
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
