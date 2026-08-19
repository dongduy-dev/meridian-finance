package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaLoanProductPolicyRepository extends JpaRepository<LoanProductPolicyJpaEntity, UUID> {

    @Query(value = """
            SELECT policy.*
            FROM loan_product_policies policy
            JOIN loan_products product
                ON product.id = policy.loan_product_id
            WHERE product.product_code = 'SALARY_ADVANCE'
              AND policy.policy_code = 'DEFAULT_POLICY'
              AND policy.active = TRUE
            """, nativeQuery = true)
    Optional<LoanProductPolicyJpaEntity> findActiveSalaryAdvanceDefaultPolicy();

    @Query(value = """
            SELECT policy.*
            FROM loan_product_policies policy
            JOIN loan_products product
                ON product.id = policy.loan_product_id
            WHERE product.product_code = 'UNSECURED_CONSUMER_LOAN'
              AND policy.policy_code = 'DEFAULT_POLICY'
              AND policy.active = TRUE
            """, nativeQuery = true)
    Optional<LoanProductPolicyJpaEntity> findActiveUnsecuredConsumerLoanDefaultPolicy();

    @Query(value = """
            SELECT policy.*
            FROM loan_product_policies policy
            JOIN loan_products product
                ON product.id = policy.loan_product_id
            WHERE product.product_code = 'COLLATERAL_LOAN'
              AND policy.policy_code = 'DEFAULT_POLICY'
              AND policy.active = TRUE
            """, nativeQuery = true)
    Optional<LoanProductPolicyJpaEntity> findActiveCollateralLoanDefaultPolicy();

    @Query(value = """
            SELECT term_months
            FROM loan_product_policy_terms
            WHERE loan_product_policy_id = :policyId
            ORDER BY term_months
            """, nativeQuery = true)
    List<Integer> findTermMonthsByPolicyId(@Param("policyId") UUID policyId);
}
