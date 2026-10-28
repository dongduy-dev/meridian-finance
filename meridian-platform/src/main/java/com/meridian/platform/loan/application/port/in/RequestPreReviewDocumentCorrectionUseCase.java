package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanCorrectionReasonCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import java.util.UUID;

public interface RequestPreReviewDocumentCorrectionUseCase {
    void requestReplacement(UUID loanApplicationId, UUID checklistItemId, UUID baselineVersionId,
            LoanCorrectionReasonCode reasonCode, String customerInstruction, BusinessOperationContext operation);
}
