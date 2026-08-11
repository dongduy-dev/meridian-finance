package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanOfferPolicy;

import java.util.Optional;

public interface UnsecuredConsumerLoanOfferPolicyRepository {

    Optional<UnsecuredConsumerLoanOfferPolicy> findActiveDefaultPolicy();
}
