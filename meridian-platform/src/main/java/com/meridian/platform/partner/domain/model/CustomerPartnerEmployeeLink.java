package com.meridian.platform.partner.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record CustomerPartnerEmployeeLink(
        UUID id,
        UUID customerId,
        UUID partnerCompanyId,
        UUID partnerEmployeeId,
        UUID sourceImportBatchId,
        EmployeeVerificationOutcome verificationOutcome,
        CustomerPartnerEmployeeLinkStatus linkStatus,
        String verifiedIdentityRef,
        String verifiedEmployeeCode,
        LocalDateTime lastVerifiedAt,
        LocalDateTime lastRefreshedAt
) {

    public static CustomerPartnerEmployeeLink verified(
            UUID id,
            UUID customerId,
            PartnerEmployee partnerEmployee,
            String verifiedIdentityRef,
            String verifiedEmployeeCode,
            LocalDateTime verifiedAt
    ) {
        Objects.requireNonNull(partnerEmployee, "partnerEmployee must not be null");

        return new CustomerPartnerEmployeeLink(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(customerId, "customerId must not be null"),
                partnerEmployee.partnerCompanyId(),
                partnerEmployee.id(),
                partnerEmployee.importBatchId(),
                EmployeeVerificationOutcome.MATCHED_ACTIVE,
                CustomerPartnerEmployeeLinkStatus.VERIFIED,
                Objects.requireNonNull(verifiedIdentityRef, "verifiedIdentityRef must not be null"),
                Objects.requireNonNull(verifiedEmployeeCode, "verifiedEmployeeCode must not be null"),
                Objects.requireNonNull(verifiedAt, "verifiedAt must not be null"),
                verifiedAt
        );
    }

    public CustomerPartnerEmployeeLink refreshVerifiedLink(
            PartnerEmployee partnerEmployee,
            String verifiedIdentityRef,
            String verifiedEmployeeCode,
            LocalDateTime refreshedAt
    ) {
        Objects.requireNonNull(partnerEmployee, "partnerEmployee must not be null");

        return new CustomerPartnerEmployeeLink(
                id,
                customerId,
                partnerEmployee.partnerCompanyId(),
                partnerEmployee.id(),
                partnerEmployee.importBatchId(),
                EmployeeVerificationOutcome.MATCHED_ACTIVE,
                CustomerPartnerEmployeeLinkStatus.VERIFIED,
                Objects.requireNonNull(verifiedIdentityRef, "verifiedIdentityRef must not be null"),
                Objects.requireNonNull(verifiedEmployeeCode, "verifiedEmployeeCode must not be null"),
                Objects.requireNonNull(refreshedAt, "refreshedAt must not be null"),
                refreshedAt
        );
    }

    public boolean isVerified() {
        return linkStatus == CustomerPartnerEmployeeLinkStatus.VERIFIED;
    }

    public boolean hasSameVerifiedEvidence(String identityReference, String employeeCode) {
        return Objects.equals(verifiedIdentityRef, identityReference)
                && Objects.equals(verifiedEmployeeCode, employeeCode);
    }
}
