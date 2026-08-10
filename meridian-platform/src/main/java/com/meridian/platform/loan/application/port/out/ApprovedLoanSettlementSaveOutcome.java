package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.ApprovedLoanSettlement;

import java.util.Objects;

public sealed interface ApprovedLoanSettlementSaveOutcome {

    record Inserted(ApprovedLoanSettlement settlement)
            implements ApprovedLoanSettlementSaveOutcome {
        public Inserted {
            Objects.requireNonNull(settlement, "settlement must not be null");
        }
    }

    record ExistingRequest(ApprovedLoanSettlement settlement)
            implements ApprovedLoanSettlementSaveOutcome {
        public ExistingRequest {
            Objects.requireNonNull(settlement, "settlement must not be null");
        }
    }

    record Conflict(ConflictKind kind)
            implements ApprovedLoanSettlementSaveOutcome {
        public Conflict {
            Objects.requireNonNull(kind, "kind must not be null");
        }
    }

    record UnresolvedConflict() implements ApprovedLoanSettlementSaveOutcome {
    }

    enum ConflictKind {
        LOAN_ACCOUNT,
        REPAYMENT_TRANSACTION,
        SETTLEMENT_ID
    }
}
