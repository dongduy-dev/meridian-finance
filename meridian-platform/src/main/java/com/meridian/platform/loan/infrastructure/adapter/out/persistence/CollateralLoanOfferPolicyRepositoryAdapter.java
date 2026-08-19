package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.CollateralLoanOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.CollateralLoanOfferPolicy;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Optional;

@Repository
public class CollateralLoanOfferPolicyRepositoryAdapter
        implements CollateralLoanOfferPolicyRepository {

    private final JpaLoanProductPolicyRepository jpaLoanProductPolicyRepository;

    public CollateralLoanOfferPolicyRepositoryAdapter(
            JpaLoanProductPolicyRepository jpaLoanProductPolicyRepository
    ) {
        this.jpaLoanProductPolicyRepository = jpaLoanProductPolicyRepository;
    }

    @Override
    public Optional<CollateralLoanOfferPolicy> findActiveDefaultPolicy() {
        return jpaLoanProductPolicyRepository.findActiveCollateralLoanDefaultPolicy()
                .map(this::toDomain);
    }

    private CollateralLoanOfferPolicy toDomain(LoanProductPolicyJpaEntity entity) {
        if (entity.getInterestCalculationMethod() == null
                || entity.getFlatMonthlyInterestRate() == null
                || entity.getFeeAmount() == null
                || entity.getRepaymentMethod() == null) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    "Collateral Loan offer policy is missing required executable pricing fields."
            );
        }

        return new CollateralLoanOfferPolicy(
                entity.getId(),
                entity.getInterestCalculationMethod(),
                entity.getFlatMonthlyInterestRate(),
                entity.getFeeAmount(),
                entity.getRepaymentMethod(),
                entity.getOfferValidityDays(),
                new HashSet<>(jpaLoanProductPolicyRepository.findTermMonthsByPolicyId(entity.getId()))
        );
    }
}
