package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;

import java.util.UUID;

public interface LoanApplicationStatusTransitionRepository {

    int nextSequenceNumber(UUID loanApplicationId);

    LoanApplicationStatusTransition save(LoanApplicationStatusTransition transition);
}
