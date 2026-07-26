package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanContractRepositoryAdapter implements LoanContractRepository {
    private final JpaLoanContractRepository contracts;
    private final JpaLoanContractRepaymentItemRepository items;
    private final EntityManager entityManager;

    public LoanContractRepositoryAdapter(
            JpaLoanContractRepository contracts,
            JpaLoanContractRepaymentItemRepository items,
            EntityManager entityManager
    ) {
        this.contracts = contracts;
        this.items = items;
        this.entityManager = entityManager;
    }

    @Override public void acquirePreparationRequestLock(UUID requestId) {
        acquireRequestLock("loan-contract:prepare-request:" + requestId);
    }
    @Override public void acquireAcknowledgmentRequestLock(UUID requestId) {
        acquireRequestLock("loan-contract:acknowledge-request:" + requestId);
    }
    @Override public void acquireConfirmationRequestLock(UUID requestId) {
        acquireRequestLock("loan-contract:confirm-request:" + requestId);
    }
    private void acquireRequestLock(String lockKey) {
        entityManager.createNativeQuery("""
                        WITH lock AS (
                            SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))
                        )
                        SELECT 1 FROM lock
                        """)
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
    @Override public LoanContract save(LoanContract contract) { return persist(contract, false); }
    @Override public LoanContract saveAndFlush(LoanContract contract) { return persist(contract, true); }

    private LoanContract persist(LoanContract contract, boolean flush) {
        boolean existing = contracts.existsById(contract.id());
        LoanContractJpaEntity entity = contracts.findById(contract.id()).map(found -> {
            found.updateLifecycle(contract); return found;
        }).orElseGet(() -> new LoanContractJpaEntity(contract));
        LoanContractJpaEntity saved = flush ? contracts.saveAndFlush(entity) : contracts.save(entity);
        if (!existing) {
            items.saveAll(contract.repaymentItems().stream()
                    .map(item -> new LoanContractRepaymentItemJpaEntity(contract.id(), item)).toList());
        }
        return toDomain(saved);
    }

    @Override public Optional<LoanContract> findCurrentByApplicationId(UUID applicationId) {
        return contracts.findByLoanApplicationIdAndStatusNot(applicationId, LoanContractStatus.SUPERSEDED).map(this::toDomain);
    }
    @Override public Optional<LoanContract> findCurrentByApplicationIdForUpdate(UUID applicationId) {
        return contracts.findCurrentForUpdate(applicationId).map(this::toDomain);
    }
    @Override public Optional<LoanContract> findByPreparationRequestId(UUID requestId) {
        return contracts.findByPreparationRequestId(requestId).map(this::toDomain);
    }
    @Override public Optional<LoanContract> findByAcknowledgmentRequestId(UUID requestId) {
        return contracts.findByAcknowledgmentRequestId(requestId).map(this::toDomain);
    }
    @Override public Optional<LoanContract> findByConfirmationRequestId(UUID requestId) {
        return contracts.findByConfirmationRequestId(requestId).map(this::toDomain);
    }
    @Override public int nextVersion(UUID applicationId) { return contracts.maximumVersion(applicationId) + 1; }

    private LoanContract toDomain(LoanContractJpaEntity entity) {
        return entity.toDomain(items.findByLoanContractIdOrderByInstallmentNumberAsc(entity.id()).stream()
                .map(LoanContractRepaymentItemJpaEntity::toDomain).toList());
    }
}
