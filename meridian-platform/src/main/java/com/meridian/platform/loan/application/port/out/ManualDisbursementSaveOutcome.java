package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.ManualDisbursement;

import java.util.Objects;

public sealed interface ManualDisbursementSaveOutcome {

    record Inserted(ManualDisbursement manualDisbursement)
            implements ManualDisbursementSaveOutcome {

        public Inserted {
            Objects.requireNonNull(manualDisbursement, "manualDisbursement must not be null");
        }
    }

    record ExistingRequest(ManualDisbursement manualDisbursement)
            implements ManualDisbursementSaveOutcome {

        public ExistingRequest {
            Objects.requireNonNull(manualDisbursement, "manualDisbursement must not be null");
        }
    }

    record Conflict(ConflictKind kind) implements ManualDisbursementSaveOutcome {

        public Conflict {
            Objects.requireNonNull(kind, "kind must not be null");
        }
    }

    record UnresolvedConflict() implements ManualDisbursementSaveOutcome {
    }

    enum ConflictKind {
        LOAN_APPLICATION,
        LOAN_CONTRACT,
        LOAN_ACCOUNT,
        EXTERNAL_TRANSFER_REFERENCE,
        DISBURSEMENT_ID
    }
}
