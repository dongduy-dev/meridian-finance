package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalaryAdvanceApplicationPolicyTest {

    private final SalaryAdvanceApplicationPolicy policy = new SalaryAdvanceApplicationPolicy();
    private final LoanProduct product = new LoanProduct(
            UUID.randomUUID(),
            ProductCode.SALARY_ADVANCE,
            ProductType.SALARY_BASED,
            "Salary Advance",
            null,
            true,
            new BigDecimal("500000.00"),
            new BigDecimal("10000000.00")
    );

    @Test
    void acceptsMathematicallyWholeVndAmountsAtSupportedTransportScales() {
        assertDoesNotThrow(() -> policy.validateRequestedAmount(product, new BigDecimal("3000000")));
        assertDoesNotThrow(() -> policy.validateRequestedAmount(product, new BigDecimal("3000000.0")));
        assertDoesNotThrow(() -> policy.validateRequestedAmount(product, new BigDecimal("3000000.00")));
    }

    @Test
    void rejectsNonZeroFractionalVndAmounts() {
        BusinessRuleViolationException fiftyXu = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedAmount(product, new BigDecimal("3000000.50"))
        );
        BusinessRuleViolationException oneXu = assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedAmount(product, new BigDecimal("3000000.01"))
        );

        assertEquals("INVALID_PRODUCT_AMOUNT", fiftyXu.getErrorCode());
        assertEquals("Requested amount must be a whole VND amount.", fiftyXu.getMessage());
        assertEquals("INVALID_PRODUCT_AMOUNT", oneXu.getErrorCode());
    }
}
