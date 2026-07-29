package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.RepaymentTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepaymentTransactionRepository {

    RepaymentTransactionSaveOutcome save(RepaymentTransaction transaction);

    Optional<RepaymentTransaction> findById(UUID transactionId);

    Optional<RepaymentTransaction> findByRequestId(UUID requestId);

    Optional<RepaymentTransaction> findByExternalPaymentReference(String reference);

    List<RepaymentTransaction> findByLoanAccountId(UUID loanAccountId);
}
