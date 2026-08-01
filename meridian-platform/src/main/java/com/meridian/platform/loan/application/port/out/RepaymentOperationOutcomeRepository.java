package com.meridian.platform.loan.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface RepaymentOperationOutcomeRepository {

    RepaymentOperationOutcome save(RepaymentOperationOutcome outcome);

    Optional<RepaymentOperationOutcome> findByRepaymentTransactionId(
            UUID repaymentTransactionId
    );
}
