package com.meridian.platform.loan.infrastructure.adapter.in.document;

import com.meridian.platform.document.application.spi.loan.LoanDocumentCorrectionSpi;
import com.meridian.platform.loan.application.port.in.AuthorizeDocumentCorrectionUploadUseCase;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class LoanDocumentCorrectionAdapter implements LoanDocumentCorrectionSpi {
    private final AuthorizeDocumentCorrectionUploadUseCase authorization;

    public LoanDocumentCorrectionAdapter(AuthorizeDocumentCorrectionUploadUseCase authorization) {
        this.authorization = authorization;
    }

    @Override
    public void authorizeCustomerUpload(UUID loanApplicationId, UUID checklistItemId, UUID expectedCurrentVersionId) {
        authorization.authorizeCustomerUpload(loanApplicationId, checklistItemId, expectedCurrentVersionId);
    }

    @Override
    public StaffUploadAuthority authorizeStaffUpload(UUID loanApplicationId, UUID checklistItemId, UUID expectedCurrentVersionId) {
        return StaffUploadAuthority.valueOf(authorization.authorizeStaffUpload(
                loanApplicationId, checklistItemId, expectedCurrentVersionId).name());
    }
}
