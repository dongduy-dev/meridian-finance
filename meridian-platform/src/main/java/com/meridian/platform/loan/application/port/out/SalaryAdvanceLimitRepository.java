package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;

import java.util.Optional;
import java.util.UUID;

public interface SalaryAdvanceLimitRepository {

    void acquireCustomerLinkLock(UUID customerId, UUID customerPartnerEmployeeLinkId);

    Optional<SalaryAdvanceLimit> findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    );

    SalaryAdvanceLimit save(SalaryAdvanceLimit salaryAdvanceLimit);
}
