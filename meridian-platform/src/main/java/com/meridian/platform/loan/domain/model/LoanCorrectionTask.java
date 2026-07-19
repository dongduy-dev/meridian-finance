package com.meridian.platform.loan.domain.model;

import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record LoanCorrectionTask(
        UUID id,
        UUID correctionRequestId,
        int sequence,
        LoanCorrectionResponsibility responsibleParty,
        LoanCorrectionScope scope,
        DocumentType documentType,
        boolean createChecklistItem,
        UUID checklistItemId,
        UUID baselineDocumentVersionId,
        String customerInstruction,
        String staffInstruction,
        LoanCorrectionTaskStatus status,
        UUID completedByUserId,
        UUID completionRequestId,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
    public LoanCorrectionTask complete(UUID actorUserId, UUID requestId, LocalDateTime at) {
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(at);
        if (status == LoanCorrectionTaskStatus.COMPLETED) {
            if (requestId.equals(completionRequestId) && actorUserId.equals(completedByUserId)) {
                return this;
            }
            throw new BusinessStateConflictException(
                    "CORRECTION_TASK_ALREADY_COMPLETED",
                    "Correction task was already completed."
            );
        }
        return new LoanCorrectionTask(
                id, correctionRequestId, sequence, responsibleParty, scope, documentType,
                createChecklistItem, checklistItemId, baselineDocumentVersionId,
                customerInstruction, staffInstruction, LoanCorrectionTaskStatus.COMPLETED,
                actorUserId, requestId, at, createdAt
        );
    }
}
