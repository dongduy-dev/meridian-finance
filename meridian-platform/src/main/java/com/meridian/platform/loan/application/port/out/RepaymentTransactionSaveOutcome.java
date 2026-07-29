package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.RepaymentTransaction;

import java.util.Objects;

public sealed interface RepaymentTransactionSaveOutcome {

    record Inserted(RepaymentTransaction transaction)
            implements RepaymentTransactionSaveOutcome {
        public Inserted {
            Objects.requireNonNull(transaction, "transaction must not be null");
        }
    }

    record ExistingRequest(RepaymentTransaction transaction)
            implements RepaymentTransactionSaveOutcome {
        public ExistingRequest {
            Objects.requireNonNull(transaction, "transaction must not be null");
        }
    }

    record Conflict(ConflictKind kind) implements RepaymentTransactionSaveOutcome {
        public Conflict {
            Objects.requireNonNull(kind, "kind must not be null");
        }
    }

    record UnresolvedConflict() implements RepaymentTransactionSaveOutcome {
    }

    enum ConflictKind {
        EXTERNAL_PAYMENT_REFERENCE,
        TRANSACTION_ID
    }
}
