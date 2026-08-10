package com.meridian.platform.loan.infrastructure.adapter.out.partner;

import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityAssessment;
import com.meridian.platform.loan.domain.model.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.partner.application.port.in.QueryCustomerPartnerEmployeeLinkUseCase;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PartnerEligibilityAdapter implements PartnerEligibilityPort {

    private final QueryCustomerPartnerEmployeeLinkUseCase queryCustomerPartnerEmployeeLinkUseCase;

    public PartnerEligibilityAdapter(QueryCustomerPartnerEmployeeLinkUseCase queryCustomerPartnerEmployeeLinkUseCase) {
        this.queryCustomerPartnerEmployeeLinkUseCase = queryCustomerPartnerEmployeeLinkUseCase;
    }

    @Override
    public Optional<VerifiedPartnerEmployeeLinkSnapshot> findVerifiedEmployeeLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        return inspectEmployeeLink(customerId, customerPartnerEmployeeLinkId).optionalSnapshot();
    }

    @Override
    public PartnerEligibilityAssessment inspectEmployeeLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        return toLoanAssessment(queryCustomerPartnerEmployeeLinkUseCase.inspectEligibility(
                customerId,
                customerPartnerEmployeeLinkId
        ));
    }

    @Override
    public PartnerEligibilityAssessment inspectCurrentEmployeeLink(UUID customerId) {
        return toLoanAssessment(queryCustomerPartnerEmployeeLinkUseCase.inspectCurrentEligibility(customerId));
    }

    private PartnerEligibilityAssessment toLoanAssessment(
            com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto assessment
    ) {
        if (assessment.status()
                != com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto.Status.ELIGIBLE) {
            return PartnerEligibilityAssessment.ineligible(
                    PartnerEligibilityAssessment.Status.valueOf(assessment.status().name())
            );
        }
        return PartnerEligibilityAssessment.eligible(
                assessment.optionalSnapshot()
                        .map(snapshot -> new VerifiedPartnerEmployeeLinkSnapshot(
                        snapshot.customerId(),
                        snapshot.customerPartnerEmployeeLinkId(),
                        snapshot.partnerCompanyId(),
                        snapshot.partnerEmployeeId(),
                        snapshot.sourceImportBatchId(),
                        SalaryAdvanceEmployeeVerificationOutcome.valueOf(snapshot.employeeVerificationOutcome()),
                        snapshot.partnerCompanySalaryAdvanceLimit(),
                        snapshot.employeeSalaryAmount(),
                        snapshot.employeeSalaryAdvanceLimit(),
                        snapshot.lastVerifiedAt(),
                        snapshot.lastRefreshedAt()
                ))
                        .orElseThrow()
        );
    }
}
