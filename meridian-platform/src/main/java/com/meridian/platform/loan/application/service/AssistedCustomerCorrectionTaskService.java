package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.CustomerCorrectionTaskDto;
import com.meridian.platform.loan.application.port.in.CompleteAssistedCustomerCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AssistedCustomerCorrectionTaskService
        implements CompleteAssistedCustomerCorrectionTaskUseCase {
    private final LoanApplicationRepository applications;
    private final LoanCorrectionRepository corrections;
    private final CustomerCorrectionDocumentProof documentProof;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public AssistedCustomerCorrectionTaskService(
            LoanApplicationRepository applications,
            LoanCorrectionRepository corrections,
            CustomerCorrectionDocumentProof documentProof,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.applications = applications;
        this.corrections = corrections;
        this.documentProof = documentProof;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CustomerCorrectionTaskDto complete(
            UUID loanApplicationId,
            UUID taskId,
            CompleteCorrectionTaskRequest command
    ) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireStaffActor(actor);
        applications.acquireWorkflowLock(loanApplicationId);
        LoanApplication application = applications.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."));
        if (!application.permitsStaffMediatedCustomerCorrection()) {
            throw new AuthorizationException(
                    "STAFF_CORRECTION_ACCESS_DENIED",
                    "Staff cannot mediate Customer correction for this Loan Application."
            );
        }
        if (application.status() != LoanApplicationStatus.RETURNED_FOR_REVISION) {
            throw new BusinessStateConflictException(
                    "CORRECTION_REQUEST_CONFLICT", "Correction request is no longer actionable."
            );
        }
        LoanCorrectionRequest request = corrections.findActiveRequestByApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new BusinessStateConflictException(
                        "CORRECTION_REQUEST_CONFLICT", "No active correction request is available."));
        LoanCorrectionTask task = corrections.findTaskByIdForUpdate(taskId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CORRECTION_TASK_NOT_FOUND", "Correction task was not found."));
        if (!task.correctionRequestId().equals(request.id())
                || task.responsibleParty() != LoanCorrectionResponsibility.CUSTOMER) {
            throw new AuthorizationException(
                    "STAFF_CORRECTION_ACCESS_DENIED",
                    "Staff cannot complete this Customer correction task."
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LoanCorrectionTask completed = task.complete(
                actor.userId(), command.completionRequestId(), now);
        if (completed == task) {
            return toDto(request, task);
        }
        documentProof.requireSatisfied(loanApplicationId, task);
        completed = corrections.saveTask(completed);
        List<LoanCorrectionTask> tasks = corrections.findTasksByRequestIdForUpdate(request.id());
        if (tasks.stream().allMatch(candidate -> candidate.status() == LoanCorrectionTaskStatus.COMPLETED)) {
            corrections.saveRequest(request.markReady(tasks, now));
        }

        BusinessOperationContext operation = BusinessOperationContext.user(
                UUID.randomUUID(), actor.userId(), now);
        auditPublisher.publish(BusinessAuditEvent.single(operation, new BusinessAuditEntry(
                BusinessAuditAction.CORRECTION_TASK_COMPLETED,
                BusinessAuditEntityType.LOAN_CORRECTION_TASK,
                completed.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.CUSTOMER_ID, application.customerId())
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplicationId)
                        .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, request.id())
                        .put(BusinessAuditPayloadKey.CORRECTION_TASK_ID, completed.id())
                        .build()
        )));
        return toDto(request, completed);
    }

    private static void requireStaffActor(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:correction:staff")) {
            throw new AuthorizationException(
                    "STAFF_CORRECTION_ACCESS_DENIED",
                    "Staff correction permission is required."
            );
        }
    }

    private static CustomerCorrectionTaskDto toDto(
            LoanCorrectionRequest request,
            LoanCorrectionTask task
    ) {
        return new CustomerCorrectionTaskDto(
                task.id(), task.correctionRequestId(), task.status().name(), task.scope().name(),
                task.documentType() == null ? null : task.documentType().name(), task.checklistItemId(),
                task.scope() == LoanCorrectionScope.DOCUMENT_REPLACEMENT
                        ? "DOCUMENT_REPLACEMENT_REQUIRED" : request.reasonCode().name(),
                task.customerInstruction(), task.createdAt(), task.completedAt()
        );
    }
}
