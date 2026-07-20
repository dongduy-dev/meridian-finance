package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.util.UUID;

public interface CompleteDocumentUploadsUseCase {
    void completeDocumentUploads(UUID loanApplicationId, BusinessOperationContext operationContext);
}
