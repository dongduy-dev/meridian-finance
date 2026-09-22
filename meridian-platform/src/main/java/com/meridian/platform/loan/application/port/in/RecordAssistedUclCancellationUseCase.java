package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public interface RecordAssistedUclCancellationUseCase {

    Result record(Command command);

    record Command(
            UUID requestId,
            UUID loanApplicationId,
            UUID expectedCorrectionRequestId,
            UUID evidenceDocumentVersionId
    ) {
        public Command {
            if (requestId == null || loanApplicationId == null
                    || expectedCorrectionRequestId == null || evidenceDocumentVersionId == null) {
                throw new BusinessRuleViolationException(
                        "ASSISTED_UCL_CANCELLATION_COMMAND_INVALID",
                        "Assisted UCL cancellation command identifiers are required."
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
    }
}
