package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.out.LoanDocumentCorrectionPort;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class LoanDocumentCorrectionAdapter implements LoanDocumentCorrectionPort {
    private final LoanCorrectionRepository correctionRepository;
    private final LoanApplicationRepository applicationRepository;

    public LoanDocumentCorrectionAdapter(
            LoanCorrectionRepository correctionRepository,
            LoanApplicationRepository applicationRepository
    ) {
        this.correctionRepository = correctionRepository;
        this.applicationRepository = applicationRepository;
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
    public StaffUploadAuthority authorizeStaffUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    ) {
        LoanCorrectionTask task = correctionRepository.findOpenStaffDocumentTask(
                        loanApplicationId, checklistItemId).orElse(null);
        StaffUploadAuthority authority = StaffUploadAuthority.STAFF_CORRECTION;
        if (task == null) {
            LoanApplication application = applicationRepository.findById(loanApplicationId)
                    .orElseThrow(() -> new AuthorizationException(
                            "DOCUMENT_UPLOAD_DENIED",
                            "No correction task authorizes this document upload."
                    ));
            if (!application.permitsStaffMediatedCustomerCorrection()
                    || application.status() != LoanApplicationStatus.RETURNED_FOR_REVISION) {
                throw new AuthorizationException(
                        "DOCUMENT_UPLOAD_DENIED",
                        "No correction task authorizes this document upload."
                );
            }
            task = correctionRepository.findOpenCustomerDocumentTask(
                            loanApplicationId, checklistItemId)
                    .orElseThrow(() -> new AuthorizationException(
                            "DOCUMENT_UPLOAD_DENIED",
                            "No open assisted Customer correction task authorizes this document upload."
                    ));
            authority = StaffUploadAuthority.ASSISTED_CUSTOMER_CORRECTION;
        }
        if (!Objects.equals(task.baselineDocumentVersionId(), expectedCurrentVersionId)) {
            throw new BusinessStateConflictException(
                    "STALE_DOCUMENT_VERSION",
                    "The staff correction task document baseline no longer matches."
            );
        }
        return authority;
    }
}
