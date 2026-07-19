package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.application.dto.CorrectionPlanRequest;
import com.meridian.platform.approval.application.dto.CorrectionTaskRequest;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
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

@Service
public class CustomerCorrectionWorkflowService {
    private final LoanCorrectionRepository correctionRepository;
    private final LoanReviewCycleRepository reviewCycleRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final BusinessAuditPublisher auditPublisher;

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
        if (correctionRepository.findActiveRequestByApplicationIdForUpdate(application.id()).isPresent()) {
            throw new BusinessStateConflictException(
                    "ACTIVE_CORRECTION_REQUEST_EXISTS",
                    "Loan Application already has an active correction request."
            );
        }
        reviewCycleRepository.save(activeCycle.requireCorrection(operationContext.occurredAt()));
        LoanCorrectionRequest request = correctionRepository.saveRequest(new LoanCorrectionRequest(
                UUID.randomUUID(),
                application.id(),
                activeCycle.id(),
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

        auditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.CORRECTION_REQUEST_CREATED,
                        BusinessAuditEntityType.LOAN_CORRECTION_REQUEST,
                        request.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, application.id())
                                .put(BusinessAuditPayloadKey.REVIEW_CYCLE_ID, activeCycle.id())
                                .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, request.id())
                                .put(BusinessAuditPayloadKey.CORRECTION_REASON_CODE, reasonCode)
                                .build()
                )
        ));
        return request;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
