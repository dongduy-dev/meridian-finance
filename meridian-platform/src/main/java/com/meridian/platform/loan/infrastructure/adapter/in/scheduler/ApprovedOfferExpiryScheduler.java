package com.meridian.platform.loan.infrastructure.adapter.in.scheduler;

import com.meridian.platform.loan.application.port.in.ExpireApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "meridian.loan.offer-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ApprovedOfferExpiryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovedOfferExpiryScheduler.class);

    private final ApprovedOfferRepository approvedOfferRepository;
    private final ExpireApprovedOfferUseCase expireApprovedOfferUseCase;
    private final Clock clock;
    private final int batchSize;

    public ApprovedOfferExpiryScheduler(
            ApprovedOfferRepository approvedOfferRepository,
            ExpireApprovedOfferUseCase expireApprovedOfferUseCase,
            Clock clock,
            @Value("${meridian.loan.offer-expiry.batch-size:100}") int batchSize
    ) {
        this.approvedOfferRepository = approvedOfferRepository;
        this.expireApprovedOfferUseCase = expireApprovedOfferUseCase;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${meridian.loan.offer-expiry.fixed-delay-ms:60000}")
    public void expireDueOffers() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (UUID loanApplicationId : approvedOfferRepository.findExpiredPendingLoanApplicationIds(now, batchSize)) {
            try {
                expireApprovedOfferUseCase.expireDueOffer(loanApplicationId, now);
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to expire approved offer for loan application {}", loanApplicationId, exception);
            }
        }
    }
}
