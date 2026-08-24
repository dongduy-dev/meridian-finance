package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.salaryadvance.VerifiedPartnerEmployeeLinkSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface PartnerEligibilityPort {

    Optional<VerifiedPartnerEmployeeLinkSnapshot> findVerifiedEmployeeLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    );

    default PartnerEligibilityAssessment inspectEmployeeLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        return findVerifiedEmployeeLink(customerId, customerPartnerEmployeeLinkId)
                .map(PartnerEligibilityAssessment::eligible)
                .orElseGet(() -> PartnerEligibilityAssessment.ineligible(
                        PartnerEligibilityAssessment.Status.NOT_VERIFIED
                ));
    }

    default PartnerEligibilityAssessment inspectCurrentEmployeeLink(UUID customerId) {
        return PartnerEligibilityAssessment.ineligible(
                PartnerEligibilityAssessment.Status.NOT_VERIFIED
        );
    }
}
