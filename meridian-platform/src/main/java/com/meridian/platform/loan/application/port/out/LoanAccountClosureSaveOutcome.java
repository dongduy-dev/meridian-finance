package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanAccountClosure;

import java.util.Objects;

public sealed interface LoanAccountClosureSaveOutcome {

    record Inserted(LoanAccountClosure closure)
            implements LoanAccountClosureSaveOutcome {
        public Inserted {
            Objects.requireNonNull(closure, "closure must not be null");
        }
    }

    record ExistingRequest(LoanAccountClosure closure)
            implements LoanAccountClosureSaveOutcome {
        public ExistingRequest {
            Objects.requireNonNull(closure, "closure must not be null");
        }
    }

    record Conflict(ConflictKind kind) implements LoanAccountClosureSaveOutcome {
        public Conflict {
            Objects.requireNonNull(kind, "kind must not be null");
        }
    }

    record UnresolvedConflict() implements LoanAccountClosureSaveOutcome {
    }

    enum ConflictKind {
        LOAN_ACCOUNT,
        CLOSURE_ID
    }
}
