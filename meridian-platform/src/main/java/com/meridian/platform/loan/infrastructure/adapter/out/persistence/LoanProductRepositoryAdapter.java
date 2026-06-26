package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LoanProductRepositoryAdapter implements LoanProductRepository {

    private final JpaLoanProductRepository jpaLoanProductRepository;

    public LoanProductRepositoryAdapter(JpaLoanProductRepository jpaLoanProductRepository) {
        this.jpaLoanProductRepository = jpaLoanProductRepository;
    }

    @Override
    public List<LoanProduct> findAllActive() {
        return jpaLoanProductRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<LoanProduct> findByProductCode(ProductCode productCode) {
        return jpaLoanProductRepository.findByProductCode(productCode.name())
                .map(this::toDomain);
    }

    private LoanProduct toDomain(LoanProductJpaEntity entity) {
        return new LoanProduct(
                entity.getId(),
                ProductCode.valueOf(entity.getProductCode()),
                ProductType.valueOf(entity.getProductType()),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getMinAmount(),
                entity.getMaxAmount()
        );
    }
}