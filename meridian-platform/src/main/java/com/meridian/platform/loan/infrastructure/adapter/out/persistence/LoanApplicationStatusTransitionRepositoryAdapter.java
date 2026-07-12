package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class LoanApplicationStatusTransitionRepositoryAdapter
        implements LoanApplicationStatusTransitionRepository {

    private final JpaLoanApplicationStatusTransitionRepository jpaRepository;

    public LoanApplicationStatusTransitionRepositoryAdapter(
            JpaLoanApplicationStatusTransitionRepository jpaRepository
    ) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int nextSequenceNumber(UUID loanApplicationId) {
        return jpaRepository.nextSequenceNumber(loanApplicationId);
    }

    @Override
    public LoanApplicationStatusTransition save(LoanApplicationStatusTransition transition) {
        return jpaRepository.save(new LoanApplicationStatusTransitionJpaEntity(transition)).toDomain();
    }
}
