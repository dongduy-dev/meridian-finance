package com.meridian.platform.document.application.port.out;

import java.util.UUID;

public interface LoanDocumentCorrectionPort {
    void authorizeCustomerUpload(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID expectedCurrentVersionId
    );
}
