package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class LoanProductActivationPolicyResolver {

    private final Map<ProductCode, LoanProductActivationPolicy> policies;

    public LoanProductActivationPolicyResolver(List<LoanProductActivationPolicy> policies) {
        Objects.requireNonNull(policies, "policies must not be null");
        EnumMap<ProductCode, LoanProductActivationPolicy> registered =
                new EnumMap<>(ProductCode.class);
        for (LoanProductActivationPolicy policy : policies) {
            Objects.requireNonNull(policy, "policy must not be null");
            ProductCode productCode = Objects.requireNonNull(
                    policy.supportedProduct(),
                    "supportedProduct must not be null"
            );
            if (registered.putIfAbsent(productCode, policy) != null) {
                throw new IllegalStateException(
                        "Duplicate Loan product activation policy registration: " + productCode
                );
            }
        }
        this.policies = Map.copyOf(registered);
    }

    public LoanProductActivationPolicy resolve(ProductCode productCode) {
        LoanProductActivationPolicy policy = policies.get(
                Objects.requireNonNull(productCode, "productCode must not be null")
        );
        if (policy == null) {
            throw new BusinessRuleViolationException(
                    "PRODUCT_ACTIVATION_NOT_SUPPORTED",
                    "Loan product activation is not supported."
            );
        }
        return policy;
    }
}
