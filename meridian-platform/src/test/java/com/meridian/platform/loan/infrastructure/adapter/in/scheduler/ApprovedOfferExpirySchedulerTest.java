package com.meridian.platform.loan.infrastructure.adapter.in.scheduler;

import com.meridian.platform.loan.application.port.in.ExpireApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.ExpiryDiscoveryTrigger;
import com.meridian.platform.shared.domain.model.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApprovedOfferExpirySchedulerTest {

    private static final UUID FIRST_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND_APPLICATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void selectsDueCandidatesAndContinuesAfterSingleFailure() {
        FakeApprovedOfferRepository repository = new FakeApprovedOfferRepository();
        FailingFirstExpireUseCase useCase = new FailingFirstExpireUseCase();
        ApprovedOfferExpiryScheduler scheduler = new ApprovedOfferExpiryScheduler(
                repository,
                useCase,
                Clock.fixed(Instant.parse("2026-07-06T05:00:00Z"), ZoneOffset.UTC),
                100
        );

        scheduler.expireDueOffers();

        assertEquals(100, repository.requestedBatchSize);
        assertEquals(List.of(FIRST_APPLICATION_ID, SECOND_APPLICATION_ID), useCase.attemptedApplicationIds);
        assertEquals(List.of(ExpiryDiscoveryTrigger.SCHEDULED_SCAN, ExpiryDiscoveryTrigger.SCHEDULED_SCAN), useCase.triggers);
        assertEquals(ActorType.SYSTEM, useCase.contexts.getFirst().actorType());
        assertNull(useCase.contexts.getFirst().actorUserId());
        assertEquals(LocalDateTime.of(2026, 7, 6, 5, 0), useCase.contexts.getFirst().occurredAt());
    }

    private static class FakeApprovedOfferRepository implements ApprovedOfferRepository {

        private int requestedBatchSize;

        @Override
        public ApprovedOffer save(ApprovedOffer approvedOffer) {
            return approvedOffer;
        }

        @Override
        public Optional<ApprovedOffer> findByLoanApplicationId(UUID loanApplicationId) {
            return Optional.empty();
        }

        @Override
        public Optional<ApprovedOffer> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
            return Optional.empty();
        }

        @Override
        public List<UUID> findExpiredPendingLoanApplicationIds(LocalDateTime now, int batchSize) {
            requestedBatchSize = batchSize;
            return List.of(FIRST_APPLICATION_ID, SECOND_APPLICATION_ID);
        }
    }

    private static class FailingFirstExpireUseCase implements ExpireApprovedOfferUseCase {

        private final List<UUID> attemptedApplicationIds = new ArrayList<>();
        private final List<BusinessOperationContext> contexts = new ArrayList<>();
        private final List<ExpiryDiscoveryTrigger> triggers = new ArrayList<>();

        @Override
        public void expireDueOffer(
                UUID loanApplicationId,
                BusinessOperationContext operationContext,
                ExpiryDiscoveryTrigger trigger
        ) {
            attemptedApplicationIds.add(loanApplicationId);
            contexts.add(operationContext);
            triggers.add(trigger);
            if (loanApplicationId.equals(FIRST_APPLICATION_ID)) {
                throw new IllegalStateException("first item failed");
            }
        }
    }
}
