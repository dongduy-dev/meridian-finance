package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.util.UUID;

public interface LoanDocumentChecklistPort {

    SubmissionChecklistInitialState resolveSubmissionInitialState(ProductCode productCode);

    void createSubmissionChecklist(
            UUID loanApplicationId,
            ProductCode productCode,
            BusinessOperationContext operationContext
    );

    boolean isProcessingReady(UUID loanApplicationId);

    record SubmissionChecklistInitialState(boolean uploadComplete) {
    }
}
