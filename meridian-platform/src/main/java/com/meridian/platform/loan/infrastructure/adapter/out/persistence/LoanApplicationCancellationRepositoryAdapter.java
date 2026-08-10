package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanApplicationCancellationRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationCancellation;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanApplicationCancellationRepositoryAdapter
        implements LoanApplicationCancellationRepository {

    private final JpaLoanApplicationCancellationRepository cancellations;

    public LoanApplicationCancellationRepositoryAdapter(
            JpaLoanApplicationCancellationRepository cancellations
    ) {
        this.cancellations = cancellations;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireCancellationRequestLock(UUID requestId) {
        cancellations.acquireCancellationRequestLock(requestId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean saveIfAbsent(LoanApplicationCancellation cancellation) {
        return cancellations.insertIfNoConflict(
                cancellation.id(),
                cancellation.loanApplicationId(),
                cancellation.correctionRequestId(),
                cancellation.reservationReleaseMovementId(),
                cancellation.requestId(),
                cancellation.cancelledByUserId(),
                cancellation.cancelledAt()
        ) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<LoanApplicationCancellation> findByRequestId(UUID requestId) {
        return cancellations.findByRequestId(requestId)
                .map(LoanApplicationCancellationJpaEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<LoanApplicationCancellation> findByLoanApplicationId(UUID loanApplicationId) {
        return cancellations.findByLoanApplicationId(loanApplicationId)
                .map(LoanApplicationCancellationJpaEntity::toDomain);
    }
}
