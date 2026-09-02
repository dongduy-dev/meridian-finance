package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;

import java.util.List;
import java.util.UUID;

public interface LoanApplicationStatusTransitionRepository {

    int nextSequenceNumber(UUID loanApplicationId);

    LoanApplicationStatusTransition save(LoanApplicationStatusTransition transition);

    default List<LoanApplicationStatusTransition> findByLoanApplicationIdOrderBySequenceNumberAsc(
            UUID loanApplicationId
    ) {
        throw new UnsupportedOperationException("Transition history query is not implemented.");
    }

    default long countMatching(
            UUID loanApplicationId,
            LoanApplicationStatus fromStatus,
            LoanApplicationStatus toStatus,
            LoanApplicationTransitionAction action
    ) {
        throw new UnsupportedOperationException(
                "Transition evidence query is not implemented."
        );
    }
}
