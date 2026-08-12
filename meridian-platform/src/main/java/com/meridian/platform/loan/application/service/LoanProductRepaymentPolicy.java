package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.RepaymentAllocation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface LoanProductRepaymentPolicy {
    ProductCode supportedProduct();
    BigDecimal releasePrincipal(PrincipalReleaseCommand command);
    void validateReleaseSemantics(ReleaseValidationCommand command);
    void validateCompletedRelease(CompletedReleaseCommand command);
    void validateCompletedPayoff(CompletedPayoffCommand command);

    record PrincipalReleaseCommand(LoanApplication application, LoanAccount account,
            UUID repaymentTransactionId, List<RepaymentAllocation> allocations,
            LocalDateTime occurredAt) {
        public PrincipalReleaseCommand {
            Objects.requireNonNull(application);
            Objects.requireNonNull(account);
            Objects.requireNonNull(repaymentTransactionId);
            allocations = List.copyOf(allocations);
            Objects.requireNonNull(occurredAt);
        }
    }

    record CompletedReleaseCommand(LoanApplication application, LoanAccount account,
            UUID repaymentTransactionId, List<RepaymentAllocation> allocations,
            BigDecimal expectedPrincipalReleased) {
        public CompletedReleaseCommand {
            Objects.requireNonNull(application);
            Objects.requireNonNull(account);
            Objects.requireNonNull(repaymentTransactionId);
            allocations = List.copyOf(allocations);
            Objects.requireNonNull(expectedPrincipalReleased);
        }
    }

    record ReleaseValidationCommand(LoanApplication application, LoanAccount account,
            UUID repaymentTransactionId, List<RepaymentAllocation> allocations,
            BigDecimal principalReleased) {
        public ReleaseValidationCommand {
            Objects.requireNonNull(application);
            Objects.requireNonNull(account);
            Objects.requireNonNull(repaymentTransactionId);
            allocations = List.copyOf(allocations);
            Objects.requireNonNull(principalReleased);
        }
    }

    record CompletedPayoffCommand(LoanApplication application, LoanAccount account,
            List<ReleaseEvidence> releases) {
        public CompletedPayoffCommand {
            Objects.requireNonNull(application);
            Objects.requireNonNull(account);
            releases = List.copyOf(releases);
        }
    }

    record ReleaseEvidence(UUID repaymentTransactionId, BigDecimal principalAllocated,
            BigDecimal principalReleased) {
        public ReleaseEvidence {
            Objects.requireNonNull(repaymentTransactionId);
            Objects.requireNonNull(principalAllocated);
            Objects.requireNonNull(principalReleased);
        }
    }
}
