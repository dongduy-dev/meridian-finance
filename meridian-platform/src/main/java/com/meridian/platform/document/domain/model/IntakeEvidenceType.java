package com.meridian.platform.document.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.util.LinkedHashSet;
import java.util.Set;

public enum IntakeEvidenceType {
    CUSTOMER_IDENTITY,
    UCL_PAPER_APPLICATION,
    COLLATERAL_PAPER_APPLICATION;

    private static final Set<String> COMMON_APPLICATION_FIELDS = Set.of(
            "fullName", "identityReference", "phoneNumber", "residentialAddress",
            "employmentStatus", "employerName", "bankCode", "bankNameSnapshot",
            "accountHolderName", "accountNumber", "requestedAmount", "requestedTermMonths"
    );

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

    public Set<String> reviewableFields() {
        if (this == CUSTOMER_IDENTITY) return Set.of("fullName", "identityReference");
        if (this == UCL_PAPER_APPLICATION) return COMMON_APPLICATION_FIELDS;
        LinkedHashSet<String> fields = new LinkedHashSet<>(COMMON_APPLICATION_FIELDS);
        fields.addAll(Set.of(
                "collateral.type", "collateral.description", "collateral.estimatedValue",
                "collateral.ownershipStatus", "collateral.conditionNote"
        ));
        return Set.copyOf(fields);
    }
}
