package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface QueryRepaymentsUseCase {

    PageResult query(UUID loanApplicationId, int page, int size);

    record PageResult(
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<Item> items
    ) {
        public PageResult {
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        }
    }

    record Item(
            UUID repaymentTransactionId,
            BigDecimal receivedAmount,
            LocalDate paymentValueDate,
            LocalDateTime recordedAt,
            BigDecimal principalAllocated,
            BigDecimal principalReleased,
            LoanAccountStatus accountStatus,
            RecordRepaymentUseCase.AccountBalance accountBalance,
            List<RecordRepaymentUseCase.Allocation> allocations,
            List<RecordRepaymentUseCase.InstallmentProgress> affectedInstallments
    ) {
        public Item {
            Objects.requireNonNull(repaymentTransactionId);
            Objects.requireNonNull(receivedAmount);
            Objects.requireNonNull(paymentValueDate);
            Objects.requireNonNull(recordedAt);
            Objects.requireNonNull(principalAllocated);
            Objects.requireNonNull(principalReleased);
            Objects.requireNonNull(accountStatus);
            Objects.requireNonNull(accountBalance);
            allocations = List.copyOf(Objects.requireNonNull(allocations));
            affectedInstallments = List.copyOf(Objects.requireNonNull(affectedInstallments));
        }

        @Override
        public String toString() {
            return "Item[repaymentTransactionId=" + repaymentTransactionId
                    + ", financialAndActorEvidence=redacted]";
        }
    }
}
