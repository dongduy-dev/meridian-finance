package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanAccountRepositoryAdapter implements LoanAccountRepository {

    private final JpaLoanAccountRepository loanAccounts;

    public LoanAccountRepositoryAdapter(JpaLoanAccountRepository loanAccounts) {
        this.loanAccounts = loanAccounts;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LoanAccount save(LoanAccount loanAccount) {
        if (loanAccounts.existsById(loanAccount.id())) {
            throw conflict();
        }
        try {
            return loanAccounts.saveAndFlush(new LoanAccountJpaEntity(loanAccount)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LoanAccount updateServicingState(LoanAccount loanAccount) {
        LoanAccountJpaEntity entity = loanAccounts.findByIdForUpdate(loanAccount.id())
                .orElseThrow(LoanAccountRepositoryAdapter::conflict);
        if (!entity.toDomain().id().equals(loanAccount.id())) {
            throw conflict();
        }
        entity.applyServicingState(loanAccount);
        try {
            return loanAccounts.saveAndFlush(entity).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Override
    public Optional<LoanAccount> findById(UUID loanAccountId) {
        return loanAccounts.findById(loanAccountId).map(LoanAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<LoanAccount> findByLoanApplicationId(UUID loanApplicationId) {
        return loanAccounts.findByLoanApplicationId(loanApplicationId)
                .map(LoanAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<LoanAccount> findByLoanContractId(UUID loanContractId) {
        return loanAccounts.findByLoanContractId(loanContractId)
                .map(LoanAccountJpaEntity::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LoanAccount> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
        return loanAccounts.findByLoanApplicationIdForUpdate(loanApplicationId)
                .map(LoanAccountJpaEntity::toDomain);
    }

    private static BusinessStateConflictException conflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan Account evidence conflicts with existing state."
        );
    }
}
