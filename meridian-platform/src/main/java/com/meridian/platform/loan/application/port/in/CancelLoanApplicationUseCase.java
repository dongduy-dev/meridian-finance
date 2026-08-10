package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public interface CancelLoanApplicationUseCase {

    Result cancel(Command command);

    record Command(UUID requestId, UUID loanApplicationId) {
        public Command {
            if (requestId == null || loanApplicationId == null) {
                throw new BusinessRuleViolationException(
                        "LOAN_APPLICATION_CANCELLATION_COMMAND_INVALID",
                        "Cancellation command identifiers are required."
                );
            }
        }

        @Override
        public String toString() {
            return "Command[loanApplicationId=" + loanApplicationId
                    + ", cancellationEvidence=redacted]";
        }
    }

    record Result(
            UUID loanApplicationId,
            LoanApplicationStatus resultingStatus,
            LocalDateTime cancelledAt,
            boolean idempotentReplay
    ) {
        public Result {
            Objects.requireNonNull(loanApplicationId);
            Objects.requireNonNull(resultingStatus);
            Objects.requireNonNull(cancelledAt);
            if (resultingStatus != LoanApplicationStatus.CANCELLED) {
                throw new IllegalArgumentException("Cancellation result must be CANCELLED.");
            }
        }

        @Override
        public String toString() {
            return "Result[loanApplicationId=" + loanApplicationId
                    + ", resultingStatus=" + resultingStatus
                    + ", cancellationEvidence=redacted]";
        }
    }
}
