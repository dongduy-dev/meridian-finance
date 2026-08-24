package com.meridian.platform.loan.domain.service.collateral;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollateralLoanApplicationPolicyTest {

    private final CollateralLoanApplicationPolicy policy = new CollateralLoanApplicationPolicy();

    @Test
    void usesCurrentCatalogBoundsAndWholeVnd() {
        assertDoesNotThrow(() -> policy.validateRequestedAmount(product(), new BigDecimal("5000000")));
        assertDoesNotThrow(() -> policy.validateRequestedAmount(product(), new BigDecimal("100000000")));
        assertEquals("INVALID_PRODUCT_AMOUNT", assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedAmount(product(), new BigDecimal("4999999"))
        ).getErrorCode());
        assertEquals("INVALID_PRODUCT_AMOUNT", assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedAmount(product(), new BigDecimal("100000001"))
        ).getErrorCode());
        assertEquals("INVALID_PRODUCT_AMOUNT", assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedAmount(product(), new BigDecimal("5000000.50"))
        ).getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 12, 18, 24})
    void acceptsOnlyApprovedTerms(int term) {
        assertDoesNotThrow(() -> policy.validateRequestedTerm(term));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 9, 36})
    void rejectsOtherTerms(int term) {
        assertEquals("INVALID_PRODUCT_TERM", assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedTerm(term)
        ).getErrorCode());
    }

    @Test
    void requiresActiveSecuredCollateralProduct() {
        LoanProduct inactive = new LoanProduct(
                product().id(), ProductCode.COLLATERAL_LOAN, ProductType.SECURED, "Collateral Loan",
                null, false, product().minAmount(), product().maxAmount()
        );
        assertEquals("PRODUCT_INACTIVE", assertThrows(
                BusinessRuleViolationException.class, () -> policy.validateProduct(inactive)
        ).getErrorCode());

        LoanProduct wrongType = new LoanProduct(
                product().id(), ProductCode.COLLATERAL_LOAN, ProductType.UNSECURED, "Collateral Loan",
                null, true, product().minAmount(), product().maxAmount()
        );
        assertEquals("PRODUCT_POLICY_INVALID", assertThrows(
                BusinessRuleViolationException.class, () -> policy.validateProduct(wrongType)
        ).getErrorCode());

        LoanProduct wrongProduct = new LoanProduct(
                product().id(), ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.SECURED,
                "Wrong Product", null, true, product().minAmount(), product().maxAmount()
        );
        assertEquals("PRODUCT_POLICY_INVALID", assertThrows(
                BusinessRuleViolationException.class, () -> policy.validateProduct(wrongProduct)
        ).getErrorCode());
    }

    private LoanProduct product() {
        return new LoanProduct(
                UUID.randomUUID(), ProductCode.COLLATERAL_LOAN, ProductType.SECURED, "Collateral Loan",
                null, true, new BigDecimal("5000000"), new BigDecimal("100000000")
        );
    }
}
