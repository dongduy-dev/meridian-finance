package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanProductActivationPolicyResolverTest {

    @Test
    void resolvesTheExactRegisteredPolicy() {
        LoanProductActivationPolicy salaryAdvance = policy(ProductCode.SALARY_ADVANCE);
        LoanProductActivationPolicy unsecuredConsumerLoan =
                policy(ProductCode.UNSECURED_CONSUMER_LOAN);
        LoanProductActivationPolicy collateralLoan = policy(ProductCode.COLLATERAL_LOAN);
        LoanProductActivationPolicyResolver resolver =
                new LoanProductActivationPolicyResolver(List.of(
                        salaryAdvance,
                        unsecuredConsumerLoan,
                        collateralLoan
                ));

        assertSame(salaryAdvance, resolver.resolve(ProductCode.SALARY_ADVANCE));
        assertSame(
                unsecuredConsumerLoan,
                resolver.resolve(ProductCode.UNSECURED_CONSUMER_LOAN)
        );
        assertSame(collateralLoan, resolver.resolve(ProductCode.COLLATERAL_LOAN));
    }

    @Test
    void rejectsDuplicatePolicyRegistrationAtConstruction() {
        assertThrows(IllegalStateException.class, () ->
                new LoanProductActivationPolicyResolver(List.of(
                        policy(ProductCode.SALARY_ADVANCE),
                        policy(ProductCode.SALARY_ADVANCE)
                ))
        );
    }

    @Test
    void rejectsUnsupportedProductWithoutFallback() {
        LoanProductActivationPolicyResolver resolver =
                new LoanProductActivationPolicyResolver(List.of(
                        policy(ProductCode.SALARY_ADVANCE)
                ));

        BusinessRuleViolationException failure = assertThrows(
                BusinessRuleViolationException.class,
                () -> resolver.resolve(ProductCode.COLLATERAL_LOAN)
        );

        assertEquals("PRODUCT_ACTIVATION_NOT_SUPPORTED", failure.getErrorCode());
    }

    private static LoanProductActivationPolicy policy(ProductCode productCode) {
        return new LoanProductActivationPolicy() {
            @Override
            public ProductCode supportedProduct() {
                return productCode;
            }

            @Override
            public ProductActivationResult activate(ProductActivationCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void validateCompletedActivation(
                    CompletedActivationValidationCommand command
            ) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
