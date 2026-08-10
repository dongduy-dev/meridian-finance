package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.ApprovedLoanSettlementRepository;
import com.meridian.platform.loan.application.port.out.ApprovedLoanSettlementSaveOutcome;
import com.meridian.platform.loan.domain.model.ApprovedLoanSettlement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ApprovedLoanSettlementRepositoryAdapter
        implements ApprovedLoanSettlementRepository {

    private final JpaApprovedLoanSettlementRepository settlements;

    public ApprovedLoanSettlementRepositoryAdapter(
            JpaApprovedLoanSettlementRepository settlements
    ) {
        this.settlements = settlements;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireApprovalRequestLock(UUID requestId) {
        settlements.acquireApprovalRequestLock(requestId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovedLoanSettlementSaveOutcome save(ApprovedLoanSettlement settlement) {
        int inserted = settlements.insertIfNoConflict(
                settlement.id(),
                settlement.loanApplicationId(),
                settlement.loanAccountId(),
                settlement.repaymentTransactionId(),
                settlement.requestId(),
                settlement.settlementAmount(),
                settlement.approvedByUserId(),
                settlement.approvedAt()
        );
        if (inserted == 1) {
            return new ApprovedLoanSettlementSaveOutcome.Inserted(settlement);
        }
        return resolveConflict(settlement);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ApprovedLoanSettlement> findByRequestId(UUID requestId) {
        return settlements.findByRequestId(requestId)
                .map(ApprovedLoanSettlementJpaEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ApprovedLoanSettlement> findByLoanAccountId(UUID loanAccountId) {
        return settlements.findByLoanAccountId(loanAccountId)
                .map(ApprovedLoanSettlementJpaEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ApprovedLoanSettlement> findByRepaymentTransactionId(
            UUID repaymentTransactionId
    ) {
        return settlements.findByRepaymentTransactionId(repaymentTransactionId)
                .map(ApprovedLoanSettlementJpaEntity::toDomain);
    }

    private ApprovedLoanSettlementSaveOutcome resolveConflict(
            ApprovedLoanSettlement attempted
    ) {
        Optional<ApprovedLoanSettlementJpaEntity> requestConflict =
                settlements.findByRequestId(attempted.requestId());
        if (requestConflict.isPresent()) {
            return new ApprovedLoanSettlementSaveOutcome.ExistingRequest(
                    requestConflict.orElseThrow().toDomain()
            );
        }
        if (settlements.findByLoanAccountId(attempted.loanAccountId()).isPresent()) {
            return conflict(
                    ApprovedLoanSettlementSaveOutcome.ConflictKind.LOAN_ACCOUNT
            );
        }
        if (settlements.findByRepaymentTransactionId(
                attempted.repaymentTransactionId()).isPresent()) {
            return conflict(
                    ApprovedLoanSettlementSaveOutcome.ConflictKind.REPAYMENT_TRANSACTION
            );
        }
        if (settlements.findById(attempted.id()).isPresent()) {
            return conflict(
                    ApprovedLoanSettlementSaveOutcome.ConflictKind.SETTLEMENT_ID
            );
        }
        return new ApprovedLoanSettlementSaveOutcome.UnresolvedConflict();
    }

    private static ApprovedLoanSettlementSaveOutcome.Conflict conflict(
            ApprovedLoanSettlementSaveOutcome.ConflictKind kind
    ) {
        return new ApprovedLoanSettlementSaveOutcome.Conflict(kind);
    }
}
