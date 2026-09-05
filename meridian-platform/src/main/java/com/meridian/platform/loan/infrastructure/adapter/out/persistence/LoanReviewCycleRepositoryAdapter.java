package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationReviewCycle;
import com.meridian.platform.loan.domain.model.LoanReviewCycleStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanReviewCycleRepositoryAdapter implements LoanReviewCycleRepository {
    private final JpaLoanReviewCycleRepository repository;

    public LoanReviewCycleRepositoryAdapter(JpaLoanReviewCycleRepository repository) {
        this.repository = repository;
    }

    @Override
    public LoanApplicationReviewCycle save(LoanApplicationReviewCycle cycle) {
        LoanReviewCycleJpaEntity entity = repository.findById(cycle.id())
                .orElseGet(() -> new LoanReviewCycleJpaEntity(cycle));
        entity.updateFrom(cycle);
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<LoanApplicationReviewCycle> findActiveByLoanApplicationId(UUID loanApplicationId) {
        return repository.findByLoanApplicationIdAndStatus(loanApplicationId, LoanReviewCycleStatus.ACTIVE)
                .map(LoanReviewCycleJpaEntity::toDomain);
    }

    @Override
    public Optional<LoanApplicationReviewCycle> findLatestByLoanApplicationId(UUID loanApplicationId) {
        return repository.findFirstByLoanApplicationIdOrderByCycleNumberDesc(loanApplicationId)
                .map(LoanReviewCycleJpaEntity::toDomain);
    }

    @Override
    public Optional<LoanApplicationReviewCycle> findActiveByLoanApplicationIdForUpdate(UUID loanApplicationId) {
        return repository.findByApplicationAndStatusForUpdate(loanApplicationId, LoanReviewCycleStatus.ACTIVE)
                .map(LoanReviewCycleJpaEntity::toDomain);
    }

    @Override
    public Optional<LoanApplicationReviewCycle> findByIdForUpdate(UUID reviewCycleId) {
        return repository.findByIdForUpdate(reviewCycleId).map(LoanReviewCycleJpaEntity::toDomain);
    }

    @Override
    public int nextCycleNumber(UUID loanApplicationId) {
        return repository.findMaximumCycleNumber(loanApplicationId) + 1;
    }
}
