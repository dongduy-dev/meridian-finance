package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;

import java.util.Optional;
import java.util.UUID;

public interface UnsecuredConsumerLoanVerificationRepository {

    UnsecuredConsumerLoanVerification save(UnsecuredConsumerLoanVerification verification);

    Optional<UnsecuredConsumerLoanVerification> findLatestByLoanApplicationId(UUID loanApplicationId);

    Optional<UnsecuredConsumerLoanVerification> findLatestByLoanApplicationIdForUpdate(
            UUID loanApplicationId
    );

    default Optional<UnsecuredConsumerLoanVerification> findByLoanApplicationId(
            UUID loanApplicationId
    ) {
        return findLatestByLoanApplicationId(loanApplicationId);
    }
}
