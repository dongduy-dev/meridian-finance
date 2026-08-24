package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceOfferPolicy;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Optional;

@Repository
public class SalaryAdvanceOfferPolicyRepositoryAdapter implements SalaryAdvanceOfferPolicyRepository {

    private final JpaLoanProductPolicyRepository jpaLoanProductPolicyRepository;

    public SalaryAdvanceOfferPolicyRepositoryAdapter(JpaLoanProductPolicyRepository jpaLoanProductPolicyRepository) {
        this.jpaLoanProductPolicyRepository = jpaLoanProductPolicyRepository;
    }

    @Override
    public Optional<SalaryAdvanceOfferPolicy> findActiveDefaultPolicy() {
        return jpaLoanProductPolicyRepository.findActiveSalaryAdvanceDefaultPolicy()
                .map(this::toDomain);
    }

    private SalaryAdvanceOfferPolicy toDomain(LoanProductPolicyJpaEntity entity) {
        if (entity.getInterestCalculationMethod() == null
                || entity.getFlatMonthlyInterestRate() == null
                || entity.getFeeAmount() == null
                || entity.getRepaymentMethod() == null) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_POLICY_INVALID",
                    "Salary Advance offer policy is missing required executable pricing fields."
            );
        }

        return new SalaryAdvanceOfferPolicy(
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
