package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanAccountClosureRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountClosureSaveOutcome;
import com.meridian.platform.loan.domain.model.LoanAccountClosure;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanAccountClosureRepositoryAdapter
        implements LoanAccountClosureRepository {

    private final JpaLoanAccountClosureRepository closures;

    public LoanAccountClosureRepositoryAdapter(
            JpaLoanAccountClosureRepository closures
    ) {
        this.closures = closures;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireClosureRequestLock(UUID requestId) {
        closures.acquireClosureRequestLock(requestId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LoanAccountClosureSaveOutcome save(LoanAccountClosure closure) {
        int inserted = closures.insertIfNoConflict(
                closure.id(),
                closure.loanApplicationId(),
                closure.loanAccountId(),
                closure.requestId(),
                closure.closedByUserId(),
                closure.closedAt()
        );
        if (inserted == 1) {
            return new LoanAccountClosureSaveOutcome.Inserted(closure);
        }
        return resolveConflict(closure);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<LoanAccountClosure> findByRequestId(UUID requestId) {
        return closures.findByRequestId(requestId)
                .map(LoanAccountClosureJpaEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<LoanAccountClosure> findByLoanAccountId(UUID loanAccountId) {
        return closures.findByLoanAccountId(loanAccountId)
                .map(LoanAccountClosureJpaEntity::toDomain);
    }

    private LoanAccountClosureSaveOutcome resolveConflict(
            LoanAccountClosure attempted
    ) {
        Optional<LoanAccountClosureJpaEntity> requestConflict =
                closures.findByRequestId(attempted.requestId());
        if (requestConflict.isPresent()) {
            return new LoanAccountClosureSaveOutcome.ExistingRequest(
                    requestConflict.orElseThrow().toDomain()
            );
        }
        if (closures.findByLoanAccountId(attempted.loanAccountId()).isPresent()) {
            return conflict(LoanAccountClosureSaveOutcome.ConflictKind.LOAN_ACCOUNT);
        }
        if (closures.findById(attempted.id()).isPresent()) {
            return conflict(LoanAccountClosureSaveOutcome.ConflictKind.CLOSURE_ID);
        }
        return new LoanAccountClosureSaveOutcome.UnresolvedConflict();
    }

    private static LoanAccountClosureSaveOutcome.Conflict conflict(
            LoanAccountClosureSaveOutcome.ConflictKind kind
    ) {
        return new LoanAccountClosureSaveOutcome.Conflict(kind);
    }
}
