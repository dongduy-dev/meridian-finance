package com.meridian.platform.partner.domain.service;

import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;

import java.util.List;
import java.util.Objects;

public class PartnerEmployeeVerificationPolicy {

    public EmployeeVerificationOutcome determineOutcome(List<PartnerEmployee> matchingEmployees) {
        Objects.requireNonNull(matchingEmployees, "matchingEmployees must not be null");

        if (matchingEmployees.isEmpty()) {
            return EmployeeVerificationOutcome.NOT_FOUND;
        }

        if (matchingEmployees.size() > 1) {
            return EmployeeVerificationOutcome.MULTIPLE_MATCHES;
        }

        PartnerEmployee partnerEmployee = matchingEmployees.get(0);
        if (partnerEmployee.active() && partnerEmployee.employmentStatus() == PartnerEmployeeStatus.ACTIVE) {
            return EmployeeVerificationOutcome.MATCHED_ACTIVE;
        }

        return EmployeeVerificationOutcome.MATCHED_INACTIVE;
    }

    public boolean requiresManualReview(EmployeeVerificationOutcome outcome) {
        return switch (outcome) {
            case NOT_FOUND, MULTIPLE_MATCHES, PENDING_MANUAL_REVIEW -> true;
            case MATCHED_ACTIVE, MATCHED_INACTIVE, MANUAL_REVIEW_APPROVED, MANUAL_REVIEW_REJECTED -> false;
        };
    }
}
