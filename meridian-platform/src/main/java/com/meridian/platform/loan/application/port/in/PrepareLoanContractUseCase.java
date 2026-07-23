package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.ContractSupersessionReason;
import com.meridian.platform.loan.domain.model.LoanContract;
import java.util.UUID;

public interface PrepareLoanContractUseCase {
    LoanContract prepare(Command command);
    record Command(UUID requestId, UUID loanApplicationId, int expectedCurrentVersion,
                   ContractSupersessionReason supersessionReason) {}
}
