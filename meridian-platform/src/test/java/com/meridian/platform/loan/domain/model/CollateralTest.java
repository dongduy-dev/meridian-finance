package com.meridian.platform.loan.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollateralTest {

    @ParameterizedTest
    @EnumSource(CollateralType.class)
    void createsEverySupportedTypeAndNormalizesCustomerText(CollateralType type) {
        Collateral collateral = Collateral.submitted(
                UUID.randomUUID(),
                collateralApplication(),
                type,
                "  2024 registered asset  ",
                new BigDecimal("35000000"),
                "  Customer-provided ownership statement  ",
                "  Normal used condition  ",
                LocalDateTime.parse("2026-08-13T09:00:00")
        );

        assertEquals(type, collateral.collateralType());
        assertEquals("2024 registered asset", collateral.description());
        assertEquals("Customer-provided ownership statement", collateral.ownershipStatus());
        assertEquals("Normal used condition", collateral.conditionNote());
    }

    @Test
    void rejectsBlankOversizedAndNonWholeValueFacts() {
        assertThrows(IllegalArgumentException.class, () -> collateral(" ", new BigDecimal("1"), "owner", "ok"));
        assertThrows(IllegalArgumentException.class, () -> collateral("asset", BigDecimal.ONE, " ", "ok"));
        assertThrows(IllegalArgumentException.class, () -> collateral("asset", BigDecimal.ONE, "owner", " "));
        assertThrows(IllegalArgumentException.class, () -> collateral("asset", new BigDecimal("1.50"), "owner", "ok"));
        assertThrows(IllegalArgumentException.class, () -> collateral("asset", BigDecimal.ZERO, "owner", "ok"));
        assertThrows(IllegalArgumentException.class, () -> collateral("x".repeat(501), BigDecimal.ONE, "owner", "ok"));
        assertThrows(IllegalArgumentException.class, () -> collateral("asset", BigDecimal.ONE, "x".repeat(201), "ok"));
        assertThrows(IllegalArgumentException.class, () -> collateral("asset", BigDecimal.ONE, "owner", "x".repeat(501)));
    }

    private Collateral collateral(String description, BigDecimal value, String ownership, String condition) {
        return Collateral.submitted(
                UUID.randomUUID(), collateralApplication(), CollateralType.OTHER,
                description, value, ownership, condition, LocalDateTime.now()
        );
    }

    private LoanApplication collateralApplication() {
        return new LoanApplication(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CL-20260813-000001",
                ProductCode.COLLATERAL_LOAN, ProductType.SECURED, LoanApplicationStatus.DOCUMENTS_PENDING,
                new BigDecimal("25000000"), 12, LocalDateTime.parse("2026-08-13T09:00:00")
        );
    }
}
