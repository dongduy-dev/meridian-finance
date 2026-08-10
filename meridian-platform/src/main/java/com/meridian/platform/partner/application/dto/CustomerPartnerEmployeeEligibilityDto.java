package com.meridian.platform.partner.application.dto;

import java.util.Objects;
import java.util.Optional;

public record CustomerPartnerEmployeeEligibilityDto(
        Status status,
        CustomerPartnerEmployeeLinkSnapshotDto snapshot
) {

    public CustomerPartnerEmployeeEligibilityDto {
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.ELIGIBLE && snapshot == null) {
            throw new IllegalArgumentException("Eligible Partner assessment requires a snapshot.");
        }
        if (status != Status.ELIGIBLE && snapshot != null) {
            throw new IllegalArgumentException("Ineligible Partner assessment must not expose a snapshot.");
        }
    }

    public static CustomerPartnerEmployeeEligibilityDto eligible(
            CustomerPartnerEmployeeLinkSnapshotDto snapshot
    ) {
        return new CustomerPartnerEmployeeEligibilityDto(
                Status.ELIGIBLE,
                Objects.requireNonNull(snapshot, "snapshot must not be null")
        );
    }

    public static CustomerPartnerEmployeeEligibilityDto ineligible(Status status) {
        if (status == Status.ELIGIBLE) {
            throw new IllegalArgumentException("Eligible status requires a snapshot.");
        }
        return new CustomerPartnerEmployeeEligibilityDto(status, null);
    }

    public Optional<CustomerPartnerEmployeeLinkSnapshotDto> optionalSnapshot() {
        return Optional.ofNullable(snapshot);
    }

    public enum Status {
        ELIGIBLE,
        NOT_VERIFIED,
        PARTNER_INACTIVE,
        EMPLOYEE_INACTIVE,
        EVIDENCE_STALE
    }
}
