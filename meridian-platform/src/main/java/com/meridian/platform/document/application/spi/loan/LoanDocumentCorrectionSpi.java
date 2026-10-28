package com.meridian.platform.document.application.spi.loan;

import java.util.UUID;

public interface LoanDocumentCorrectionSpi {
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
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    );
}
