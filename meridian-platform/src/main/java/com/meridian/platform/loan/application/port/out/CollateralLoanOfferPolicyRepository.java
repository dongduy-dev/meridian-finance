package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.collateral.CollateralLoanOfferPolicy;

import java.util.Optional;

public interface CollateralLoanOfferPolicyRepository {

    Optional<CollateralLoanOfferPolicy> findActiveDefaultPolicy();
}
