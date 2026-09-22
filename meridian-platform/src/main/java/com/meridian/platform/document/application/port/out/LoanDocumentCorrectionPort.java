package com.meridian.platform.document.application.port.out;

import java.util.UUID;

public interface LoanDocumentCorrectionPort {
    enum StaffUploadAuthority {
        STAFF_CORRECTION,
        ASSISTED_CUSTOMER_CORRECTION
    }

    void authorizeCustomerUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    );

    default StaffUploadAuthority authorizeStaffUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    ) {
        throw new UnsupportedOperationException("Staff document upload is not supported.");
    }
}
