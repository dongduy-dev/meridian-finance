package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanContractRepositoryAdapter implements LoanContractRepository {
    private final JpaLoanContractRepository contracts;
    private final JpaLoanContractRepaymentItemRepository items;

    public LoanContractRepositoryAdapter(JpaLoanContractRepository contracts, JpaLoanContractRepaymentItemRepository items) {
        this.contracts = contracts;
        this.items = items;
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
