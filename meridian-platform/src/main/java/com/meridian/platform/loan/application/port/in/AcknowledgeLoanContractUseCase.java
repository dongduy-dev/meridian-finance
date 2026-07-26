package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanContract;
import java.util.UUID;

public interface AcknowledgeLoanContractUseCase {
    LoanContract acknowledge(Command command);
    record Command(UUID requestId, UUID loanApplicationId, int expectedContractVersion) {}
}
