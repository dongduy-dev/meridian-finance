package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanAccountStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanAccountStatusTransition;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class LoanAccountStatusTransitionRepositoryAdapter
        implements LoanAccountStatusTransitionRepository {

    private final JpaLoanAccountStatusTransitionRepository transitions;

    public LoanAccountStatusTransitionRepositoryAdapter(
            JpaLoanAccountStatusTransitionRepository transitions
    ) {
        this.transitions = transitions;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LoanAccountStatusTransition save(
            LoanAccountStatusTransition transition
    ) {
        return transitions.saveAndFlush(
                new LoanAccountStatusTransitionJpaEntity(transition)
        ).toDomain();
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public int nextSequenceNumber(UUID loanAccountId) {
        return transitions.nextSequenceNumber(loanAccountId);
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public List<LoanAccountStatusTransition> findByLoanAccountId(
            UUID loanAccountId
    ) {
        return transitions.findByLoanAccountIdOrderBySequenceNumberAsc(loanAccountId)
                .stream()
                .map(LoanAccountStatusTransitionJpaEntity::toDomain)
                .toList();
    }
}
