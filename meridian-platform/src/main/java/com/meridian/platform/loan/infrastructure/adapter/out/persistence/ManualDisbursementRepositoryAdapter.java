package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.ManualDisbursementRepository;
import com.meridian.platform.loan.application.port.out.ManualDisbursementSaveOutcome;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ManualDisbursementRepositoryAdapter implements ManualDisbursementRepository {

    private final JpaManualDisbursementRepository manualDisbursements;

    public ManualDisbursementRepositoryAdapter(
            JpaManualDisbursementRepository manualDisbursements
    ) {
        this.manualDisbursements = manualDisbursements;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ManualDisbursementSaveOutcome save(ManualDisbursement manualDisbursement) {
        int inserted = manualDisbursements.insertIfNoConflict(
                manualDisbursement.id(),
                manualDisbursement.loanApplicationId(),
                manualDisbursement.loanContractId(),
                manualDisbursement.loanAccountId(),
                manualDisbursement.requestId(),
                manualDisbursement.expectedContractVersion(),
                manualDisbursement.externalTransferReference(),
                manualDisbursement.disbursedAmount(),
                manualDisbursement.valueDate(),
                manualDisbursement.firstRepaymentDate(),
                manualDisbursement.confirmedByUserId(),
                manualDisbursement.confirmedAt()
        );
        if (inserted == 1) {
            return new ManualDisbursementSaveOutcome.Inserted(manualDisbursement);
        }
        return resolveConflict(manualDisbursement);
    }

    @Override
    public Optional<ManualDisbursement> findByRequestId(UUID requestId) {
        return manualDisbursements.findByRequestId(requestId)
                .map(ManualDisbursementJpaEntity::toDomain);
    }

    @Override
    public Optional<ManualDisbursement> findByLoanApplicationId(UUID loanApplicationId) {
        return manualDisbursements.findByLoanApplicationId(loanApplicationId)
                .map(ManualDisbursementJpaEntity::toDomain);
    }

    @Override
    public Optional<ManualDisbursement> findByLoanContractId(UUID loanContractId) {
        return manualDisbursements.findByLoanContractId(loanContractId)
                .map(ManualDisbursementJpaEntity::toDomain);
    }

    @Override
    public Optional<ManualDisbursement> findByLoanAccountId(UUID loanAccountId) {
        return manualDisbursements.findByLoanAccountId(loanAccountId)
                .map(ManualDisbursementJpaEntity::toDomain);
    }

    @Override
    public Optional<ManualDisbursement> findByExternalTransferReference(
            String externalTransferReference
    ) {
        return manualDisbursements.findByExternalTransferReference(
                        ManualDisbursement.canonicalReference(externalTransferReference)
                )
                .map(ManualDisbursementJpaEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ManualDisbursement> findByLoanApplicationIdForUpdate(
            UUID loanApplicationId
    ) {
        return manualDisbursements.findByLoanApplicationIdForUpdate(loanApplicationId)
                .map(ManualDisbursementJpaEntity::toDomain);
    }

    private ManualDisbursementSaveOutcome resolveConflict(
            ManualDisbursement attempted
    ) {
        Optional<ManualDisbursementJpaEntity> requestConflict =
                manualDisbursements.findByRequestId(attempted.requestId());
        if (requestConflict.isPresent()) {
            return new ManualDisbursementSaveOutcome.ExistingRequest(
                    requestConflict.orElseThrow().toDomain()
            );
        }
        if (manualDisbursements.findByLoanApplicationId(
                attempted.loanApplicationId()).isPresent()) {
            return conflict(ManualDisbursementSaveOutcome.ConflictKind.LOAN_APPLICATION);
        }
        if (manualDisbursements.findByLoanContractId(attempted.loanContractId()).isPresent()) {
            return conflict(ManualDisbursementSaveOutcome.ConflictKind.LOAN_CONTRACT);
        }
        if (manualDisbursements.findByLoanAccountId(attempted.loanAccountId()).isPresent()) {
            return conflict(ManualDisbursementSaveOutcome.ConflictKind.LOAN_ACCOUNT);
        }
        if (manualDisbursements.findByExternalTransferReference(
                attempted.externalTransferReference()).isPresent()) {
            return conflict(
                    ManualDisbursementSaveOutcome.ConflictKind.EXTERNAL_TRANSFER_REFERENCE
            );
        }
        if (manualDisbursements.findById(attempted.id()).isPresent()) {
            return conflict(ManualDisbursementSaveOutcome.ConflictKind.DISBURSEMENT_ID);
        }
        return new ManualDisbursementSaveOutcome.UnresolvedConflict();
    }

    private static ManualDisbursementSaveOutcome.Conflict conflict(
            ManualDisbursementSaveOutcome.ConflictKind kind
    ) {
        return new ManualDisbursementSaveOutcome.Conflict(kind);
    }
}
