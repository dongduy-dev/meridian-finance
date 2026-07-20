package com.meridian.platform.document.application.port.out;

import java.util.UUID;

public interface LoanDocumentCorrectionPort {
    void authorizeCustomerUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    );

    default void authorizeStaffUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    ) {
        throw new UnsupportedOperationException("Staff document upload is not supported.");
    }
}
