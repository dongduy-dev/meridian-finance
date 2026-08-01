package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanAccountStatusTransition;

import java.util.List;
import java.util.UUID;

public interface LoanAccountStatusTransitionRepository {

    LoanAccountStatusTransition save(LoanAccountStatusTransition transition);

    int nextSequenceNumber(UUID loanAccountId);

    List<LoanAccountStatusTransition> findByLoanAccountId(UUID loanAccountId);
}
