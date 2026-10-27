package com.meridian.platform.loan.application.port.in;

import java.util.UUID;

public interface AuthorizeDocumentCorrectionUploadUseCase {
    enum StaffUploadAuthority {
        STAFF_CORRECTION,
        ASSISTED_CUSTOMER_CORRECTION
    }

    void authorizeCustomerUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    );

    StaffUploadAuthority authorizeStaffUpload(
            UUID loanApplicationId, UUID checklistItemId, UUID expectedCurrentVersionId);
}
