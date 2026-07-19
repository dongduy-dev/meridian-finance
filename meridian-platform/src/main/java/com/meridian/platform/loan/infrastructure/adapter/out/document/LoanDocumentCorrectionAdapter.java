package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.out.LoanDocumentCorrectionPort;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class LoanDocumentCorrectionAdapter implements LoanDocumentCorrectionPort {
    private final LoanCorrectionRepository correctionRepository;

    public LoanDocumentCorrectionAdapter(LoanCorrectionRepository correctionRepository) {
        this.correctionRepository = correctionRepository;
    }

    @Override
    public void authorizeCustomerUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    ) {
        LoanCorrectionTask task = correctionRepository.findOpenCustomerDocumentTask(
                        loanApplicationId, checklistItemId)
                .orElseThrow(() -> new AuthorizationException(
                        "DOCUMENT_UPLOAD_DENIED",
                        "No open customer correction task authorizes this document upload."
                ));
        if (!Objects.equals(task.baselineDocumentVersionId(), expectedCurrentVersionId)) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION",
                    "The correction task document baseline no longer matches."
            );
        }
    }

    @Override
    public void authorizeStaffUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    ) {
        LoanCorrectionTask task = correctionRepository.findOpenStaffDocumentTask(
                        loanApplicationId, checklistItemId)
                .orElseThrow(() -> new AuthorizationException(
                        "DOCUMENT_UPLOAD_DENIED",
                        "No open staff correction task authorizes this document upload."
                ));
        if (!Objects.equals(task.baselineDocumentVersionId(), expectedCurrentVersionId)) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION",
                    "The staff correction task document baseline no longer matches."
            );
        }
    }
}
