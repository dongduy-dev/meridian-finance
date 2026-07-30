package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class LoanProductRepaymentPolicyResolver {
    private final Map<ProductCode, LoanProductRepaymentPolicy> policies;

    public LoanProductRepaymentPolicyResolver(List<LoanProductRepaymentPolicy> policies) {
        EnumMap<ProductCode, LoanProductRepaymentPolicy> registered =
                new EnumMap<>(ProductCode.class);
        for (LoanProductRepaymentPolicy policy : List.copyOf(policies)) {
            ProductCode product = Objects.requireNonNull(policy.supportedProduct());
            if (registered.putIfAbsent(product, policy) != null) {
                throw new IllegalStateException(
                        "Duplicate Loan product repayment policy: " + product
                );
            }
        }
        this.policies = Map.copyOf(registered);
    }

    public LoanProductRepaymentPolicy resolve(ProductCode productCode) {
        LoanProductRepaymentPolicy policy = policies.get(Objects.requireNonNull(productCode));
        if (policy == null) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_REPAYMENT_NOT_SUPPORTED",
                    "Loan product repayment is not supported."
            );
        }
        return policy;
    }
}
