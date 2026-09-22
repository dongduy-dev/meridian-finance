package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.StaffAssistedOfferResponse;

import java.util.Optional;
import java.util.UUID;

public interface StaffAssistedOfferResponseRepository {

    void acquireRequestLock(UUID requestId);

    StaffAssistedOfferResponse save(StaffAssistedOfferResponse response);

    Optional<StaffAssistedOfferResponse> findByRequestId(UUID requestId);

    Optional<StaffAssistedOfferResponse> findByApprovedOfferId(UUID approvedOfferId);
}
