package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaLoanApplicationStatusTransitionRepository
        extends JpaRepository<LoanApplicationStatusTransitionJpaEntity, UUID> {

    @Query("""
            select coalesce(max(transition.sequenceNumber), 0) + 1
            from LoanApplicationStatusTransitionJpaEntity transition
            where transition.loanApplicationId = :loanApplicationId
            """)
    int nextSequenceNumber(@Param("loanApplicationId") UUID loanApplicationId);

    long countByLoanApplicationIdAndFromStatusAndToStatusAndAction(
            UUID loanApplicationId,
            LoanApplicationStatus fromStatus,
            LoanApplicationStatus toStatus,
            LoanApplicationTransitionAction action
    );
}
