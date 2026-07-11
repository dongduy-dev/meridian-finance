package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LoanApplicationStatusTransitionRepositoryAdapter implements LoanApplicationStatusTransitionRepository {

    private final JpaLoanApplicationStatusTransitionRepository repository;

    public LoanApplicationStatusTransitionRepositoryAdapter(JpaLoanApplicationStatusTransitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveAll(List<LoanApplicationStatusTransition> transitions) {
        repository.saveAll(transitions.stream().map(LoanApplicationStatusTransitionJpaEntity::new).toList());
    }
}
