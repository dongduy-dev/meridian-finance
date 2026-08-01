package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.RepaymentTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepaymentTransactionRepository {

    void acquireRecordingRequestLock(UUID requestId);

    RepaymentTransactionSaveOutcome save(RepaymentTransaction transaction);

    Optional<RepaymentTransaction> findById(UUID transactionId);

    Optional<RepaymentTransaction> findByRequestId(UUID requestId);

    Optional<RepaymentTransaction> findByExternalPaymentReference(String reference);

    List<RepaymentTransaction> findByLoanAccountId(UUID loanAccountId);

    Page findPageByLoanAccountId(UUID loanAccountId, int page, int size);

    record Page(
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<RepaymentTransaction> transactions
    ) {
        public Page {
            transactions = List.copyOf(transactions);
        }
    }
}
