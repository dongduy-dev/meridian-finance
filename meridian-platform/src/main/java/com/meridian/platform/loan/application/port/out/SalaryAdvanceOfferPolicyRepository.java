package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceOfferPolicy;

import java.util.Optional;

public interface SalaryAdvanceOfferPolicyRepository {

    Optional<SalaryAdvanceOfferPolicy> findActiveDefaultPolicy();
}
