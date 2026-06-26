package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public class LoanApplicationRepositoryAdapter implements LoanApplicationRepository {

    private final JpaLoanApplicationRepository jpaLoanApplicationRepository;

    public LoanApplicationRepositoryAdapter(JpaLoanApplicationRepository jpaLoanApplicationRepository) {
        this.jpaLoanApplicationRepository = jpaLoanApplicationRepository;
    }

    @Override
    public LoanApplication save(LoanApplication loanApplication) {
        return toDomain(jpaLoanApplicationRepository.save(new LoanApplicationJpaEntity(loanApplication)));
    }

    @Override
    public boolean existsByCustomerIdAndProductCodeAndStatusIn(
            UUID customerId,
            ProductCode productCode,
            Set<LoanApplicationStatus> statuses
    ) {
        return jpaLoanApplicationRepository.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId,
                productCode,
                statuses
        );
    }

    @Override
    public long nextApplicationNumberSequence() {
        return jpaLoanApplicationRepository.nextApplicationNumberSequence();
    }

    private LoanApplication toDomain(LoanApplicationJpaEntity entity) {
        return new LoanApplication(
                entity.getId(),
                entity.getCustomerId(),
                entity.getLoanProductId(),
                entity.getApplicationNumber(),
                entity.getProductCode(),
                entity.getProductType(),
                entity.getStatus(),
                entity.getRequestedAmount(),
                entity.getRequestedTermMonths(),
                entity.getSubmittedAt()
        );
    }
}
