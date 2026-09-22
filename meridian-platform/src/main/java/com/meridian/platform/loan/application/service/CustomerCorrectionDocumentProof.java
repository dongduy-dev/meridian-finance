package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerCorrectionDocumentProof {
    private final LoanDocumentChecklistPort documents;

    public CustomerCorrectionDocumentProof(LoanDocumentChecklistPort documents) {
        this.documents = documents;
    }

    public void requireSatisfied(UUID loanApplicationId, LoanCorrectionTask task) {
        if (task.scope() == LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD) {
            documents.requireCurrentVersion(loanApplicationId, task.checklistItemId());
            return;
        }
        if (task.scope() == LoanCorrectionScope.DOCUMENT_REPLACEMENT
                && documents.hasCurrentVersionDifferentFrom(
                task.checklistItemId(), task.baselineDocumentVersionId())) {
            return;
        }
        throw new BusinessStateConflictException(
                "CORRECTION_TASK_PROOF_MISSING",
                "The required document correction has not been completed."
        );
    }

    public boolean isSatisfied(UUID loanApplicationId, LoanCorrectionTask task) {
        try {
            requireSatisfied(loanApplicationId, task);
            return true;
        } catch (BusinessStateConflictException exception) {
            if ("DOCUMENT_UPLOAD_REQUIRED".equals(exception.getErrorCode())
                    || "CORRECTION_TASK_PROOF_MISSING".equals(exception.getErrorCode())) {
                return false;
            }
            throw exception;
        }
    }
}
