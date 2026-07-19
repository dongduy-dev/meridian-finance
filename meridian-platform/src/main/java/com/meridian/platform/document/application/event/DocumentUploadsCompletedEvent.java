package com.meridian.platform.document.application.event;

import com.meridian.platform.shared.application.operation.BusinessOperationContext;

import java.util.UUID;

public record DocumentUploadsCompletedEvent(
        UUID loanApplicationId,
        BusinessOperationContext operationContext
) {
}
