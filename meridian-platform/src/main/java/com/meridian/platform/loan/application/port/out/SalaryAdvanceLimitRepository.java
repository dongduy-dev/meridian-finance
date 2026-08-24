package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimit;

import java.util.Optional;
import java.util.UUID;

public interface SalaryAdvanceLimitRepository {

    void acquireCustomerLinkLock(UUID customerId, UUID customerPartnerEmployeeLinkId);

    Optional<SalaryAdvanceLimit> findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    );

    default Optional<SalaryAdvanceLimit> findByCustomerIdAndCustomerPartnerEmployeeLinkId(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        return Optional.empty();
    }

    default Optional<SalaryAdvanceLimit> findLatestByCustomerId(UUID customerId) {
        return Optional.empty();
    }

    default Optional<SalaryAdvanceLimit> findById(UUID salaryAdvanceLimitId) {
        return Optional.empty();
    }

    default Optional<SalaryAdvanceLimit> findByIdForUpdate(UUID salaryAdvanceLimitId) {
        return Optional.empty();
    }

    SalaryAdvanceLimit save(SalaryAdvanceLimit salaryAdvanceLimit);
}
