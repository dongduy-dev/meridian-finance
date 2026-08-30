package com.meridian.platform.loan.domain.model;

import java.util.Set;

public final class LoanProductTermPolicy {

    private LoanProductTermPolicy() {
    }

    public static Set<Integer> allowedTermsMonths(ProductCode productCode) {
        return switch (productCode) {
            case SALARY_ADVANCE -> Set.of(1, 2, 3);
            case UNSECURED_CONSUMER_LOAN -> Set.of(3, 6, 9, 12);
            case COLLATERAL_LOAN -> Set.of(6, 12, 18, 24);
        };
    }
}
