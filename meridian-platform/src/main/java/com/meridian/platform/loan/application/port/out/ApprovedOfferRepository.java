package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.ApprovedOffer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovedOfferRepository {

    ApprovedOffer save(ApprovedOffer approvedOffer);

    Optional<ApprovedOffer> findByLoanApplicationId(UUID loanApplicationId);

    Optional<ApprovedOffer> findByLoanApplicationIdForUpdate(UUID loanApplicationId);

    List<UUID> findExpiredPendingLoanApplicationIds(LocalDateTime now, int batchSize);
}
