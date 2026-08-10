package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public interface CloseLoanAccountUseCase {

    Result close(Command command);

    record Command(UUID requestId, UUID loanApplicationId) {
        public Command {
            if (requestId == null || loanApplicationId == null) {
                throw new BusinessRuleViolationException(
                        "LOAN_ACCOUNT_CLOSURE_COMMAND_INVALID",
                        "Closure command identifiers are required."
                );
            }
        }

        @Override
        public String toString() {
            return "Command[loanApplicationId=" + loanApplicationId
                    + ", administrativeEvidence=redacted]";
        }
    }

    record Result(
            UUID loanApplicationId,
            UUID loanAccountId,
            LoanAccountStatus resultingStatus,
            LocalDateTime closedAt,
            boolean idempotentReplay
    ) {
        public Result {
            Objects.requireNonNull(loanApplicationId);
            Objects.requireNonNull(loanAccountId);
            Objects.requireNonNull(resultingStatus);
            Objects.requireNonNull(closedAt);
            if (resultingStatus != LoanAccountStatus.CLOSED) {
                throw new IllegalArgumentException(
                        "Administrative closure result must be CLOSED."
                );
            }
        }

        @Override
        public String toString() {
            return "Result[loanApplicationId=" + loanApplicationId
                    + ", loanAccountId=" + loanAccountId
                    + ", resultingStatus=" + resultingStatus
                    + ", administrativeEvidence=redacted]";
        }
    }
}
