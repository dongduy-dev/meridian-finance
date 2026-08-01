package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public interface EvaluateLoanAccountOverdueUseCase {

    Result evaluate(Command command);

    record Command(
            UUID loanApplicationId,
            UUID loanAccountId,
            LocalDate evaluationDate,
            LocalDateTime evaluatedAt
    ) {
        public Command {
            Objects.requireNonNull(loanApplicationId);
            Objects.requireNonNull(loanAccountId);
            Objects.requireNonNull(evaluationDate);
            Objects.requireNonNull(evaluatedAt);
        }
    }

    record Result(
            UUID loanApplicationId,
            UUID loanAccountId,
            LocalDate evaluationDate,
            LoanAccountStatus previousStatus,
            LoanAccountStatus resultingStatus,
            int installmentTransitionCount,
            boolean accountStatusChanged,
            boolean noOp
    ) {
    }
}
