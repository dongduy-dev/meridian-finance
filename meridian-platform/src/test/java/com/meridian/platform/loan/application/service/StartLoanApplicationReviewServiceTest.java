package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.support.CapturingAuditEventPublisher;
import com.meridian.platform.support.CapturingLoanApplicationStatusTransitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartLoanApplicationReviewServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    private FakeLoanApplicationRepository loanApplicationRepository;
    private CapturingLoanApplicationStatusTransitionRepository transitionRepository;
    private CapturingAuditEventPublisher auditEventPublisher;
    private StartLoanApplicationReviewService service;

    @BeforeEach
    void setUp() {
        loanApplicationRepository = new FakeLoanApplicationRepository();
        transitionRepository = new CapturingLoanApplicationStatusTransitionRepository();
        auditEventPublisher = new CapturingAuditEventPublisher();
        service = new StartLoanApplicationReviewService(
                loanApplicationRepository,
                new FixedCurrentUserProvider(),
                Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC),
                new LoanApplicationLifecycleHistoryRecorder(transitionRepository),
                auditEventPublisher
        );
    }

    @Test
    void startsReviewAndRecordsLifecycleHistoryAndAudit() {
        LoanApplicationReviewDto result = service.startReview(LOAN_APPLICATION_ID);

        assertEquals("UNDER_REVIEW", result.status());
        assertEquals(LoanApplicationStatus.UNDER_REVIEW, loanApplicationRepository.savedApplication.status());
        assertEquals(1, transitionRepository.transitions().size());
        assertEquals(LoanApplicationStatus.SUBMITTED, transitionRepository.transitions().get(0).fromStatus());
        assertEquals(LoanApplicationStatus.UNDER_REVIEW, transitionRepository.transitions().get(0).toStatus());
        assertEquals(USER_ID, transitionRepository.transitions().get(0).actor().userId());
        assertEquals(NOW, transitionRepository.transitions().get(0).occurredAt());
        assertEquals(1, auditEventPublisher.events().size());
        assertEquals(AuditAction.REVIEW_STARTED, auditEventPublisher.events().get(0).action());
        assertEquals(auditEventPublisher.events().get(0).operationId(), transitionRepository.transitions().get(0).operationId());
    }

    private static LoanApplication application() {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "SA-20260706-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.SUBMITTED,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                NOW.minusDays(1)
        );
    }

    private static class FixedCurrentUserProvider implements CurrentUserProvider {

        @Override
        public AuthenticatedUser currentUser() {
            return new AuthenticatedUser(
                    USER_ID,
                    "loan.officer@meridian.local",
                    "STAFF",
                    null,
                    Set.of("LOAN_OFFICER"),
                    Set.of("loan:review")
            );
        }
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
