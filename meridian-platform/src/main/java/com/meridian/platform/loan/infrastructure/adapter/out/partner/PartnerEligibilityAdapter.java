package com.meridian.platform.loan.infrastructure.adapter.out.partner;

import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
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
        return queryCustomerPartnerEmployeeLinkUseCase.findVerifiedActiveLink(customerId, customerPartnerEmployeeLinkId)
                .map(snapshot -> new VerifiedPartnerEmployeeLinkSnapshot(
                        snapshot.customerId(),
                        snapshot.customerPartnerEmployeeLinkId(),
                        snapshot.partnerCompanyId(),
                        snapshot.partnerEmployeeId(),
                        snapshot.sourceImportBatchId(),
                        snapshot.employeeSalaryAdvanceLimit(),
                        snapshot.lastVerifiedAt(),
                        snapshot.lastRefreshedAt()
                ));
    }
}
