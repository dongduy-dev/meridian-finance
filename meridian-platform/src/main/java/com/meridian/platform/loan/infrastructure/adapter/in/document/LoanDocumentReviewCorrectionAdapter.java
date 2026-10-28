package com.meridian.platform.loan.infrastructure.adapter.in.document;

import com.meridian.platform.loan.domain.model.LoanCorrectionReasonCode;
import com.meridian.platform.document.application.spi.loan.LoanDocumentReviewCorrectionSpi;
import com.meridian.platform.loan.application.port.in.RequestPreReviewDocumentCorrectionUseCase;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoanDocumentReviewCorrectionAdapter implements LoanDocumentReviewCorrectionSpi {
    private final RequestPreReviewDocumentCorrectionUseCase correctionService;

    public LoanDocumentReviewCorrectionAdapter(RequestPreReviewDocumentCorrectionUseCase correctionService) {
        this.correctionService = correctionService;
    }

    @Override
    public void requestCustomerReplacement(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID baselineDocumentVersionId,
            String reasonCode,
            String customerInstruction,
            BusinessOperationContext operationContext
    ) {
        correctionService.requestReplacement(
                loanApplicationId, checklistItemId, baselineDocumentVersionId,
                reasonCode == null ? null : LoanCorrectionReasonCode.valueOf(reasonCode), customerInstruction, operationContext
        );
    }
}
