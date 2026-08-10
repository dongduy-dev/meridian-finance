package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.StaffCorrectionTaskDto;
import com.meridian.platform.loan.application.port.in.CompleteStaffCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
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
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StaffCorrectionTaskService
        implements QueryStaffCorrectionTasksUseCase, CompleteStaffCorrectionTaskUseCase {

    private final LoanCorrectionRepository correctionRepository;
    private final LoanDocumentChecklistPort documentChecklistPort;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public StaffCorrectionTaskService(
            LoanCorrectionRepository correctionRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.correctionRepository = correctionRepository;
        this.documentChecklistPort = documentChecklistPort;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffCorrectionTaskDto> findStaffTasks(
            LoanCorrectionTaskStatus status,
            int page,
            int size
    ) {
        requireStaffPermission(currentUserProvider.currentUser());
        if (status == null || page < 0 || size < 1 || size > 50) {
            throw new BusinessRuleViolationException(
                    "INVALID_STAFF_CORRECTION_QUERY",
                    "Staff correction query requires a status, non-negative page, and size from 1 to 50."
            );
        }
        return correctionRepository.findStaffTasks(status, page, size).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public StaffCorrectionTaskDto complete(
            UUID taskId,
            CompleteCorrectionTaskRequest command
    ) {
        AuthenticatedUser user = currentUserProvider.currentUser();
        requireStaffPermission(user);
        LoanCorrectionTask task = correctionRepository.findTaskByIdForUpdate(taskId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CORRECTION_TASK_NOT_FOUND", "Correction task was not found."));
        LoanCorrectionRequest request = correctionRepository.findRequestById(task.correctionRequestId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "CORRECTION_REQUEST_NOT_FOUND", "Correction request was not found."));
        if (task.responsibleParty() != LoanCorrectionResponsibility.STAFF) {
            throw new AuthorizationException(
                    "STAFF_CORRECTION_ACCESS_DENIED", "This is not a staff-owned correction task.");
        }
        if (!request.isActive()) {
            throw new BusinessStateConflictException(
                    "CORRECTION_REQUEST_CONFLICT",
                    "Correction request is no longer actionable."
            );
        }
        if (request.createdByUserId().equals(user.userId())) {
            throw new AuthorizationException(
                    "STAFF_CORRECTION_MAKER_CHECKER_VIOLATION",
                    "The staff user who created a correction request cannot complete its staff tasks."
            );
        }

        List<LoanCorrectionTask> requestTasks =
                correctionRepository.findTasksByRequestIdForUpdate(request.id());
        LocalDateTime now = LocalDateTime.now(clock);
        LoanCorrectionTask completed = task.complete(
                user.userId(), command.completionRequestId(), now);
        if (completed == task) {
            return toDto(task);
        }
        verifyProof(request.loanApplicationId(), task, requestTasks);
        completed = correctionRepository.saveTask(completed);
        List<LoanCorrectionTask> tasks = correctionRepository.findTasksByRequestIdForUpdate(request.id());
        if (tasks.stream().allMatch(candidate -> candidate.status() == LoanCorrectionTaskStatus.COMPLETED)) {
            correctionRepository.saveRequest(request.markReady(tasks, now));
        }

        BusinessOperationContext operation = BusinessOperationContext.user(
                UUID.randomUUID(), user.userId(), now);
        auditPublisher.publish(BusinessAuditEvent.single(operation, new BusinessAuditEntry(
                BusinessAuditAction.CORRECTION_TASK_COMPLETED,
                BusinessAuditEntityType.LOAN_CORRECTION_TASK,
                completed.id(),
                BusinessAuditPayload.builder()
                        .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, request.loanApplicationId())
                        .put(BusinessAuditPayloadKey.CORRECTION_REQUEST_ID, request.id())
                        .put(BusinessAuditPayloadKey.CORRECTION_TASK_ID, completed.id())
                        .build()
        )));
        return toDto(completed);
    }

    private void verifyProof(
            UUID loanApplicationId,
            LoanCorrectionTask task,
            List<LoanCorrectionTask> requestTasks
    ) {
        if (task.scope() == LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD) {
            documentChecklistPort.requireCurrentVersion(loanApplicationId, task.checklistItemId());
            return;
        }
        if (task.scope() == LoanCorrectionScope.DOCUMENT_REVIEW) {
            boolean reviewFollowsReplacement = requestTasks.stream().anyMatch(candidate ->
                    candidate.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER
                            && candidate.scope() == LoanCorrectionScope.DOCUMENT_REPLACEMENT
                            && Objects.equals(candidate.checklistItemId(), task.checklistItemId())
                            && Objects.equals(
                                    candidate.baselineDocumentVersionId(),
                                    task.baselineDocumentVersionId()));
            UUID versionToReview = task.baselineDocumentVersionId();
            if (reviewFollowsReplacement) {
                versionToReview = documentChecklistPort.requireCurrentVersion(
                        loanApplicationId, task.checklistItemId());
                if (Objects.equals(versionToReview, task.baselineDocumentVersionId())) {
                    throw proofMissing();
                }
            }
            if (documentChecklistPort.isVersionReviewed(
                    loanApplicationId, task.checklistItemId(), versionToReview)) {
                return;
            }
        }
        throw proofMissing();
    }

    private BusinessStateConflictException proofMissing() {
        return new BusinessStateConflictException(
                "CORRECTION_TASK_PROOF_MISSING",
                "The required staff document correction has not been completed."
        );
    }

    private void requireStaffPermission(AuthenticatedUser user) {
        if (!user.hasPermission("loan:correction:staff")) {
            throw new AuthorizationException(
                    "STAFF_CORRECTION_ACCESS_DENIED",
                    "Staff correction permission is required."
            );
        }
    }

    private StaffCorrectionTaskDto toDto(LoanCorrectionTask task) {
        LoanCorrectionRequest request = correctionRepository.findRequestById(task.correctionRequestId())
                .orElseThrow();
        return new StaffCorrectionTaskDto(
                task.id(),
                task.correctionRequestId(),
                request.loanApplicationId(),
                task.status().name(),
                task.scope().name(),
                task.documentType() == null ? null : task.documentType().name(),
                task.checklistItemId(),
                task.baselineDocumentVersionId(),
                request.reasonCode().name(),
                task.staffInstruction(),
                task.createdAt(),
                task.completedAt()
        );
    }
}
