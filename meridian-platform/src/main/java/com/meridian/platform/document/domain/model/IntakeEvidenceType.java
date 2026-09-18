package com.meridian.platform.document.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

public enum IntakeEvidenceType {
    CUSTOMER_IDENTITY,
    UCL_PAPER_APPLICATION,
    COLLATERAL_PAPER_APPLICATION;

    public void requireProduct(String productCode) {
        boolean allowed = this == CUSTOMER_IDENTITY
                || (this == UCL_PAPER_APPLICATION && "UNSECURED_CONSUMER_LOAN".equals(productCode))
                || (this == COLLATERAL_PAPER_APPLICATION && "COLLATERAL_LOAN".equals(productCode));
        if (!allowed) {
            throw new BusinessRuleViolationException(
                    "INTAKE_EVIDENCE_PRODUCT_MISMATCH",
                    "Paper application evidence does not match the assisted-origination product."
            );
        }
    }
}
