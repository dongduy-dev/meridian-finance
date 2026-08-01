package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface JpaLoanAccountStatusTransitionRepository
        extends JpaRepository<LoanAccountStatusTransitionJpaEntity, UUID> {

    @Query("""
            select coalesce(max(transition.sequenceNumber), 0) + 1
            from LoanAccountStatusTransitionJpaEntity transition
            where transition.loanAccountId = :loanAccountId
            """)
    int nextSequenceNumber(@Param("loanAccountId") UUID loanAccountId);

    List<LoanAccountStatusTransitionJpaEntity>
    findByLoanAccountIdOrderBySequenceNumberAsc(UUID loanAccountId);
}
