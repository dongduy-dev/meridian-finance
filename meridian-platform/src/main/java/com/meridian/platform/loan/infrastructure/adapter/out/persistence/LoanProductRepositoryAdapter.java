package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class LoanProductRepositoryAdapter implements LoanProductRepository {

    private final JpaLoanProductRepository jpaLoanProductRepository;
    private final Clock clock;

    public LoanProductRepositoryAdapter(JpaLoanProductRepository jpaLoanProductRepository, Clock clock) {
        this.jpaLoanProductRepository = jpaLoanProductRepository;
        this.clock = clock;
    }

    @Override
    public List<LoanProduct> findAll() {
        return jpaLoanProductRepository.findAllByOrderByProductCodeAsc()
                .stream()
                .map(this::toDomain)
                .toList();
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

    @Override
    public Optional<LoanProduct> findByProductCodeForUpdate(ProductCode productCode) {
        return jpaLoanProductRepository.findByProductCodeForUpdate(productCode.name())
                .map(this::toDomain);
    }

    @Override
    public LoanProduct save(LoanProduct loanProduct) {
        LoanProductJpaEntity entity = jpaLoanProductRepository.findById(loanProduct.id())
                .orElseThrow(() -> new IllegalStateException("Locked Loan Product disappeared before persistence."));
        entity.update(
                loanProduct.active(),
                loanProduct.minAmount(),
                loanProduct.maxAmount(),
                LocalDateTime.now(clock)
        );
        return toDomain(jpaLoanProductRepository.saveAndFlush(entity));
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
