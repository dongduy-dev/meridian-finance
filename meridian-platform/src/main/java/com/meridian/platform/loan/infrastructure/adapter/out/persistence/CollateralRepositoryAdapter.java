package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.domain.model.Collateral;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class CollateralRepositoryAdapter implements CollateralRepository {

    private final JpaCollateralRepository repository;

    public CollateralRepositoryAdapter(JpaCollateralRepository repository) {
        this.repository = repository;
    }

    @Override
    public Collateral save(Collateral collateral) {
        return repository.save(new CollateralJpaEntity(collateral)).toDomain();
    }

    @Override
    public List<Collateral> findByLoanApplicationId(UUID loanApplicationId) {
        return repository.findByLoanApplicationId(loanApplicationId).stream()
                .map(CollateralJpaEntity::toDomain)
                .toList();
    }
}
