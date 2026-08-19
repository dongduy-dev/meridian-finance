package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoanProductRepaymentPolicyResolverTest {

    @Test
    void resolvesEveryExecutableRepaymentProduct() {
        LoanProductRepaymentPolicy salary = policy(ProductCode.SALARY_ADVANCE);
        LoanProductRepaymentPolicy ucl = policy(ProductCode.UNSECURED_CONSUMER_LOAN);
        LoanProductRepaymentPolicy collateral = policy(ProductCode.COLLATERAL_LOAN);
        LoanProductRepaymentPolicyResolver resolver =
                new LoanProductRepaymentPolicyResolver(List.of(salary, ucl, collateral));

        assertSame(salary, resolver.resolve(ProductCode.SALARY_ADVANCE));
        assertSame(ucl, resolver.resolve(ProductCode.UNSECURED_CONSUMER_LOAN));
        assertSame(collateral, resolver.resolve(ProductCode.COLLATERAL_LOAN));
    }

    @Test
    void rejectsDuplicateAndMissingProductPolicies() {
        LoanProductRepaymentPolicy first = policy(ProductCode.COLLATERAL_LOAN);
        LoanProductRepaymentPolicy duplicate = policy(ProductCode.COLLATERAL_LOAN);

        assertThrows(IllegalStateException.class,
                () -> new LoanProductRepaymentPolicyResolver(List.of(first, duplicate)));

        LoanProductRepaymentPolicyResolver resolver =
                new LoanProductRepaymentPolicyResolver(List.of(first));
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> resolver.resolve(ProductCode.SALARY_ADVANCE)
        );
        assertEquals("PRODUCT_REPAYMENT_NOT_SUPPORTED", exception.getErrorCode());
    }

    private static LoanProductRepaymentPolicy policy(ProductCode productCode) {
        LoanProductRepaymentPolicy policy = mock(LoanProductRepaymentPolicy.class);
        when(policy.supportedProduct()).thenReturn(productCode);
        return policy;
    }
}
