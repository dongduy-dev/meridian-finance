package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.CollateralLoanVerification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CollateralLoanVerificationRepositoryAdapter
        implements CollateralLoanVerificationRepository {

    private final JpaCollateralLoanVerificationRepository repository;

    public CollateralLoanVerificationRepositoryAdapter(
            JpaCollateralLoanVerificationRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public CollateralLoanVerification save(CollateralLoanVerification verification) {
        return repository.save(new CollateralLoanVerificationJpaEntity(verification)).toDomain();
    }

    @Override
    public Optional<CollateralLoanVerification> findByLoanApplicationId(UUID loanApplicationId) {
        return repository.findByLoanApplicationId(loanApplicationId)
                .map(CollateralLoanVerificationJpaEntity::toDomain);
    }
}
