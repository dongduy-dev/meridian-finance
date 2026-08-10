package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionSaveOutcome;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RepaymentTransactionRepositoryAdapter
        implements RepaymentTransactionRepository {

    private final JpaRepaymentTransactionRepository transactions;
    private final JpaRepaymentAllocationRepository allocations;

    public RepaymentTransactionRepositoryAdapter(
            JpaRepaymentTransactionRepository transactions,
            JpaRepaymentAllocationRepository allocations
    ) {
        this.transactions = transactions;
        this.allocations = allocations;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireRecordingRequestLock(UUID requestId) {
        transactions.acquireRecordingRequestLock(requestId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RepaymentTransactionSaveOutcome save(RepaymentTransaction transaction) {
        int inserted = transactions.insertIfNoConflict(
                transaction.id(),
                transaction.loanApplicationId(),
                transaction.loanAccountId(),
                transaction.repaymentScheduleId(),
                transaction.transactionType().name(),
                transaction.requestId(),
                transaction.externalPaymentReference(),
                transaction.receivedAmount(),
                transaction.paymentValueDate(),
                transaction.recordedByUserId(),
                transaction.recordedAt()
        );
        if (inserted == 1) {
            allocations.saveAllAndFlush(transaction.allocations().stream()
                    .map(RepaymentAllocationJpaEntity::new)
                    .toList());
            return new RepaymentTransactionSaveOutcome.Inserted(transaction);
        }
        return resolveConflict(transaction);
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public Optional<RepaymentTransaction> findById(UUID transactionId) {
        return transactions.findById(transactionId).map(this::toDomain);
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public Optional<RepaymentTransaction> findByRequestId(UUID requestId) {
        return transactions.findByRequestId(requestId).map(this::toDomain);
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public Optional<RepaymentTransaction> findByExternalPaymentReference(
            String reference
    ) {
        RepaymentTransaction.requireCanonicalReference(reference);
        return transactions.findByExternalPaymentReference(reference).map(this::toDomain);
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public List<RepaymentTransaction> findByLoanAccountId(UUID loanAccountId) {
        return transactions.findByLoanAccountIdOrderByRecordedAtAscIdAsc(loanAccountId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public Page findPageByLoanAccountId(UUID loanAccountId, int page, int size) {
        org.springframework.data.domain.Page<RepaymentTransactionJpaEntity> selected =
                transactions.findByLoanAccountIdOrderByRecordedAtDescIdDesc(
                        loanAccountId,
                        PageRequest.of(page, size)
                );
        return new Page(
                selected.getNumber(),
                selected.getSize(),
                selected.getTotalElements(),
                selected.getTotalPages(),
                selected.getContent().stream().map(this::toDomain).toList()
        );
    }

    private RepaymentTransactionSaveOutcome resolveConflict(
            RepaymentTransaction attempted
    ) {
        Optional<RepaymentTransactionJpaEntity> requestConflict =
                transactions.findByRequestId(attempted.requestId());
        if (requestConflict.isPresent()) {
            return new RepaymentTransactionSaveOutcome.ExistingRequest(
                    toDomain(requestConflict.orElseThrow())
            );
        }
        if (transactions.findByExternalPaymentReference(
                attempted.externalPaymentReference()).isPresent()) {
            return new RepaymentTransactionSaveOutcome.Conflict(
                    RepaymentTransactionSaveOutcome.ConflictKind
                            .EXTERNAL_PAYMENT_REFERENCE
            );
        }
        if (transactions.findById(attempted.id()).isPresent()) {
            return new RepaymentTransactionSaveOutcome.Conflict(
                    RepaymentTransactionSaveOutcome.ConflictKind.TRANSACTION_ID
            );
        }
        return new RepaymentTransactionSaveOutcome.UnresolvedConflict();
    }

    private RepaymentTransaction toDomain(RepaymentTransactionJpaEntity entity) {
        return entity.toDomain(
                allocations
                        .findByRepaymentTransactionIdOrderByAllocationSequenceAsc(
                                entity.id()
                        )
                        .stream()
                        .map(RepaymentAllocationJpaEntity::toDomain)
                        .toList()
        );
    }
}
