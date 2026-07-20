package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.application.port.out.LoanDocumentReviewCorrectionPort;
import com.meridian.platform.loan.application.service.PreReviewDocumentCorrectionService;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoanDocumentReviewCorrectionAdapter implements LoanDocumentReviewCorrectionPort {
    private final PreReviewDocumentCorrectionService correctionService;

    public LoanDocumentReviewCorrectionAdapter(PreReviewDocumentCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @Override
    public void requestCustomerReplacement(
            UUID loanApplicationId,
            UUID checklistItemId,
            UUID baselineDocumentVersionId,
            CorrectionReasonCode reasonCode,
            String customerInstruction,
            BusinessOperationContext operationContext
    ) {
        correctionService.requestReplacement(
                loanApplicationId, checklistItemId, baselineDocumentVersionId,
                reasonCode, customerInstruction, operationContext
        );
    }
}
