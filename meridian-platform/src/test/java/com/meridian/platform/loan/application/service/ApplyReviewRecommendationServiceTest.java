package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction;
import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.support.CapturingLoanApplicationStatusTransitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplyReviewRecommendationServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LOAN_OFFICER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID OPERATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime RECOMMENDED_AT = LocalDateTime.of(2026, 7, 6, 13, 0);

    private FakeLoanApplicationRepository loanApplicationRepository;
    private CapturingLoanApplicationStatusTransitionRepository transitionRepository;
    private ApplyReviewRecommendationService service;

    @BeforeEach
    void setUp() {
        loanApplicationRepository = new FakeLoanApplicationRepository();
        transitionRepository = new CapturingLoanApplicationStatusTransitionRepository();
        service = new ApplyReviewRecommendationService(
                loanApplicationRepository,
                new LoanApplicationLifecycleHistoryRecorder(transitionRepository)
        );
    }

    @Test
    void appliesRecommendationAndRecordsLoanOwnedLifecycleHistory() {
        LoanApplicationReviewDto result = service.applyReviewRecommendation(command(LoanReviewRecommendationAction.RECOMMEND_APPROVAL));

        assertEquals("APPROVAL_PENDING", result.status());
        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, loanApplicationRepository.savedApplication.status());
        assertEquals(1, transitionRepository.transitions().size());
        assertEquals(OPERATION_ID, transitionRepository.transitions().get(0).operationId());
        assertEquals((short) 1, transitionRepository.transitions().get(0).sequenceNumber());
        assertEquals(LOAN_APPLICATION_ID, transitionRepository.transitions().get(0).loanApplicationId());
        assertEquals(LoanApplicationStatus.UNDER_REVIEW, transitionRepository.transitions().get(0).fromStatus());
        assertEquals(LoanApplicationStatus.APPROVAL_PENDING, transitionRepository.transitions().get(0).toStatus());
        assertEquals(LoanApplicationTransitionAction.RECOMMEND_APPROVAL, transitionRepository.transitions().get(0).action());
        assertEquals("Looks eligible", transitionRepository.transitions().get(0).reason());
        assertEquals(LOAN_OFFICER_USER_ID, transitionRepository.transitions().get(0).actor().userId());
        assertEquals(RECOMMENDED_AT, transitionRepository.transitions().get(0).occurredAt());
    }

    private static ApplyReviewRecommendationCommand command(LoanReviewRecommendationAction action) {
        return new ApplyReviewRecommendationCommand(
                LOAN_APPLICATION_ID,
                RECOMMENDATION_ID,
                LOAN_OFFICER_USER_ID,
                action,
                "Looks eligible",
                RECOMMENDED_AT,
                OPERATION_ID
        );
    }

    private static LoanApplication application() {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "SA-20260706-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.UNDER_REVIEW,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                RECOMMENDED_AT.minusDays(1)
        );
    }

    private static class FakeLoanApplicationRepository implements LoanApplicationRepository {

        private LoanApplication application = application();
        private LoanApplication savedApplication;

        @Override
        public LoanApplication save(LoanApplication loanApplication) {
            savedApplication = loanApplication;
            application = loanApplication;
            return loanApplication;
        }

        @Override
        public Optional<LoanApplication> findById(UUID loanApplicationId) {
            return Optional.ofNullable(application).filter(value -> value.id().equals(loanApplicationId));
        }

        @Override
        public Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId) {
            return findById(loanApplicationId);
        }

        @Override
        public boolean existsByCustomerIdAndProductCodeAndStatusIn(
                UUID customerId,
                ProductCode productCode,
                Set<LoanApplicationStatus> statuses
        ) {
            return false;
        }

        @Override
        public long nextApplicationNumberSequence() {
            return 1L;
        }
    }
}