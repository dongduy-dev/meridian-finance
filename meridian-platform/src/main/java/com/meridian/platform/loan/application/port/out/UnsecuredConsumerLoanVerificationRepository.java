package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnsecuredConsumerLoanVerificationRepository {

    UnsecuredConsumerLoanVerification save(UnsecuredConsumerLoanVerification verification);

    Optional<UnsecuredConsumerLoanVerification> findLatestByLoanApplicationId(UUID loanApplicationId);

    default List<UnsecuredConsumerLoanVerification> findAllByLoanApplicationIdOrderByVerificationSequenceAsc(
            UUID loanApplicationId
    ) {
        return findLatestByLoanApplicationId(loanApplicationId).stream().toList();
    }

    Optional<UnsecuredConsumerLoanVerification> findLatestByLoanApplicationIdForUpdate(
            UUID loanApplicationId
    );

    default Optional<UnsecuredConsumerLoanVerification> findByLoanApplicationId(
            UUID loanApplicationId
    ) {
        return findLatestByLoanApplicationId(loanApplicationId);
    }
}
