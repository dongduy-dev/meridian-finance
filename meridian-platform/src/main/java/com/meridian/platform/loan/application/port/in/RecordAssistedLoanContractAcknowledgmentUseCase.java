package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanContract;

import java.util.UUID;

public interface RecordAssistedLoanContractAcknowledgmentUseCase {

    LoanContract record(Command command);

    record Command(
            UUID acknowledgmentRequestId,
            UUID loanApplicationId,
            UUID contractId,
            int expectedContractVersion,
            UUID evidenceDocumentVersionId
    ) {
    }
}
