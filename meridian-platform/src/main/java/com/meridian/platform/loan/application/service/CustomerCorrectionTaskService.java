package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.CustomerCorrectionTaskDto;
import com.meridian.platform.loan.application.port.in.CompleteOwnCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.QueryOwnCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
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
public class CustomerCorrectionTaskService implements QueryOwnCorrectionTasksUseCase, CompleteOwnCorrectionTaskUseCase {
    private final LoanCorrectionRepository correctionRepository;
    private final LoanApplicationRepository applicationRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public CustomerCorrectionTaskService(
            LoanCorrectionRepository correctionRepository,
            LoanApplicationRepository applicationRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.correctionRepository = correctionRepository;
        this.applicationRepository = applicationRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCorrectionTaskDto> findOwnTasks(UUID loanApplicationId) {
        UUID customerId = currentUserProvider.currentUser().requireCustomerId();
        requireOwnedApplication(loanApplicationId, customerId);
        return correctionRepository.findCustomerTasks(loanApplicationId, customerId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public CustomerCorrectionTaskDto complete(
            UUID loanApplicationId,
            UUID taskId,
            CompleteCorrectionTaskRequest command
    ) {
        AuthenticatedUser user = currentUserProvider.currentUser();
        UUID customerId = user.requireCustomerId();
        requireOwnedApplication(loanApplicationId, customerId);
        LoanCorrectionTask task = correctionRepository.findTaskByIdForUpdate(taskId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CORRECTION_TASK_NOT_FOUND", "Correction task was not found."));
        LoanCorrectionRequest request = correctionRepository.findRequestById(task.correctionRequestId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "CORRECTION_REQUEST_NOT_FOUND", "Correction request was not found."));
        if (!request.loanApplicationId().equals(loanApplicationId)
                || task.responsibleParty() != LoanCorrectionResponsibility.CUSTOMER) {
            throw new AuthorizationException(
                    "CORRECTION_ACCESS_DENIED", "Customer cannot complete this correction task.");
        }
        requireActiveRequest(request);
        LocalDateTime now = LocalDateTime.now(clock);
        LoanCorrectionTask completed = task.complete(user.userId(), command.completionRequestId(), now);
        if (completed == task) {
            return toDto(task);
        }
        verifyDocumentProof(loanApplicationId, task);
        completed = correctionRepository.saveTask(completed);
        List<LoanCorrectionTask> tasks = correctionRepository.findTasksByRequestIdForUpdate(request.id());
        if (tasks.stream().allMatch(candidate ->
                candidate.status() == com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus.COMPLETED)) {
            correctionRepository.saveRequest(request.markReady(tasks, now));
        }
        BusinessOperationContext operation = BusinessOperationContext.user(
                UUID.randomUUID(), user.userId(), now);
        auditPublisher.publish(BusinessAuditEvent.single(operation, new BusinessAuditEntry(
                BusinessAuditAction.CORRECTION_TASK_COMPLETED,
                BusinessAuditEntityType.LOAN_CORRECTION_TASK,
                completed.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplicationId)
                        .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, request.id())
                        .put(BusinessAuditPayloadKey.CORRECTION_TASK_ID, completed.id())
                        .build()
        )));
        return toDto(completed);
    }

    private void verifyDocumentProof(UUID loanApplicationId, LoanCorrectionTask task) {
        if (task.scope() == LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD) {
            documentChecklistPort.requireCurrentVersion(loanApplicationId, task.checklistItemId());
            return;
        }
        if (task.scope() == LoanCorrectionScope.DOCUMENT_REPLACEMENT
                && documentChecklistPort.hasCurrentVersionDifferentFrom(
                        task.checklistItemId(), task.baselineDocumentVersionId())) {
            return;
        }
        throw new BusinessStateConflictException(
                "CORRECTION_TASK_PROOF_MISSING",
                "The required document correction has not been completed."
        );
    }

    private LoanApplication requireOwnedApplication(UUID applicationId, UUID customerId) {
        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."));
        if (!application.customerId().equals(customerId)) {
            throw new AuthorizationException(
                    "CORRECTION_ACCESS_DENIED", "Customer cannot access another Loan Application correction.");
        }
        return application;
    }

    private static void requireActiveRequest(LoanCorrectionRequest request) {
        if (!request.isActive()) {
            throw new BusinessStateConflictException(
                    "CORRECTION_REQUEST_CONFLICT",
                    "Correction request is no longer actionable."
            );
        }
    }

    private CustomerCorrectionTaskDto toDto(LoanCorrectionTask task) {
        LoanCorrectionRequest request = correctionRepository.findRequestById(task.correctionRequestId())
                .orElseThrow();
        return new CustomerCorrectionTaskDto(
                task.id(), task.correctionRequestId(), task.status().name(), task.scope().name(),
                task.documentType() == null ? null : task.documentType().name(), task.checklistItemId(),
                task.scope() == LoanCorrectionScope.DOCUMENT_REPLACEMENT
                        ? "DOCUMENT_REPLACEMENT_REQUIRED" : request.reasonCode().name(),
                task.customerInstruction(), task.createdAt(), task.completedAt()
        );
    }
}
