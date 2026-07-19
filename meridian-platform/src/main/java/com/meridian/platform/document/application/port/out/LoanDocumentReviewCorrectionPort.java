package com.meridian.platform.document.application.port.out;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.util.UUID;

public interface LoanDocumentReviewCorrectionPort {
    void requestCustomerReplacement(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID baselineDocumentVersionId,
            CorrectionReasonCode reasonCode,
            String customerInstruction,
            BusinessOperationContext operationContext
    );
}
