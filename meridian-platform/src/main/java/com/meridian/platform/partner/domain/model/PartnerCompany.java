package com.meridian.platform.partner.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PartnerCompany(
        UUID id,
        String companyCode,
        String name,
        PartnerCompanyStatus status,
        BigDecimal salaryAdvancePolicyLimit
) {
    public PartnerCompany {
        Objects.requireNonNull(id, "id must not be null");
        companyCode = requiredText(companyCode, 50, "companyCode");
        name = requiredText(name, 200, "name");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(salaryAdvancePolicyLimit, "salaryAdvancePolicyLimit must not be null");
        if (salaryAdvancePolicyLimit.signum() < 0
                || salaryAdvancePolicyLimit.scale() > 2
                || salaryAdvancePolicyLimit.precision() - salaryAdvancePolicyLimit.scale() > 17) {
            throw new IllegalArgumentException("salaryAdvancePolicyLimit must be a nonnegative monetary amount");
        }
    }

    public PartnerCompany update(String updatedName, BigDecimal updatedPolicyLimit) {
        return new PartnerCompany(id, companyCode, updatedName, status, updatedPolicyLimit);
    }

    public PartnerCompany changeStatus(PartnerCompanyStatus updatedStatus) {
        return new PartnerCompany(id, companyCode, name, updatedStatus, salaryAdvancePolicyLimit);
    }

    private static String requiredText(String value, int maximumLength, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
