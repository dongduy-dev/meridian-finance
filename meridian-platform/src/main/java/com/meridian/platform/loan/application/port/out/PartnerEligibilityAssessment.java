package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;

import java.util.Objects;
import java.util.Optional;

public record PartnerEligibilityAssessment(
        Status status,
        VerifiedPartnerEmployeeLinkSnapshot snapshot
) {

    public PartnerEligibilityAssessment {
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.ELIGIBLE && snapshot == null) {
            throw new IllegalArgumentException("Eligible Partner assessment requires a snapshot.");
        }
        if (status != Status.ELIGIBLE && snapshot != null) {
            throw new IllegalArgumentException("Ineligible Partner assessment must not contain a snapshot.");
        }
    }

    public static PartnerEligibilityAssessment eligible(VerifiedPartnerEmployeeLinkSnapshot snapshot) {
        return new PartnerEligibilityAssessment(
                Status.ELIGIBLE,
                Objects.requireNonNull(snapshot, "snapshot must not be null")
        );
    }

    public static PartnerEligibilityAssessment ineligible(Status status) {
        if (status == Status.ELIGIBLE) {
            throw new IllegalArgumentException("Eligible status requires a snapshot.");
        }
        return new PartnerEligibilityAssessment(status, null);
    }

    public Optional<VerifiedPartnerEmployeeLinkSnapshot> optionalSnapshot() {
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
