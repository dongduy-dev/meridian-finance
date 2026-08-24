package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanOfferPolicy;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Optional;

@Repository
public class UnsecuredConsumerLoanOfferPolicyRepositoryAdapter
        implements UnsecuredConsumerLoanOfferPolicyRepository {

    private final JpaLoanProductPolicyRepository jpaLoanProductPolicyRepository;

    public UnsecuredConsumerLoanOfferPolicyRepositoryAdapter(
            JpaLoanProductPolicyRepository jpaLoanProductPolicyRepository
    ) {
        this.jpaLoanProductPolicyRepository = jpaLoanProductPolicyRepository;
    }

    @Override
    public Optional<UnsecuredConsumerLoanOfferPolicy> findActiveDefaultPolicy() {
        return jpaLoanProductPolicyRepository.findActiveUnsecuredConsumerLoanDefaultPolicy()
                .map(this::toDomain);
    }

    private UnsecuredConsumerLoanOfferPolicy toDomain(LoanProductPolicyJpaEntity entity) {
        if (entity.getInterestCalculationMethod() == null
                || entity.getFlatMonthlyInterestRate() == null
                || entity.getFeeAmount() == null
                || entity.getRepaymentMethod() == null) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    "Unsecured Consumer Loan offer policy is missing required executable pricing fields."
            );
        }

        return new UnsecuredConsumerLoanOfferPolicy(
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
