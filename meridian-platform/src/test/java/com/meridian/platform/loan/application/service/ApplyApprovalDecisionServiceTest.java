package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.LoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.ProductVerificationResult;
import com.meridian.platform.loan.domain.model.SalaryAdvanceEmployeeVerificationOutcome;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitStatus;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyApprovalDecisionServiceTest {

    private static final UUID LOAN_APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DECISION_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID APPROVER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LINK_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LIMIT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    private FakeLoanApplicationRepository loanApplicationRepository;
    private FakeSalaryAdvanceVerificationRepository verificationRepository;
    private FakeSalaryAdvanceLimitRepository limitRepository;
    private FakeSalaryAdvanceLimitMovementRepository movementRepository;
    private ApplyApprovalDecisionService service;

    @BeforeEach
    void setUp() {
        loanApplicationRepository = new FakeLoanApplicationRepository();
        verificationRepository = new FakeSalaryAdvanceVerificationRepository();
        limitRepository = new FakeSalaryAdvanceLimitRepository();
        movementRepository = new FakeSalaryAdvanceLimitMovementRepository();
        service = new ApplyApprovalDecisionService(
                loanApplicationRepository,
                verificationRepository,
                limitRepository,
                movementRepository
        );
    }

    @Test
    void appliesApprovalDecisionWithoutReleasingSalaryAdvanceLimit() {
        LoanApplicationReviewDto result = service.applyApprovalDecision(command(LoanApprovalDecisionAction.APPROVE));

        assertEquals(LOAN_APPLICATION_ID, result.loanApplicationId());
        assertEquals("APPROVED", result.status());
        assertEquals(LoanApplicationStatus.APPROVED, loanApplicationRepository.savedApplication.status());
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    @Test
    void appliesRejectionDecisionAndReleasesSalaryAdvanceReservation() {
        LoanApplicationReviewDto result = service.applyApprovalDecision(command(LoanApprovalDecisionAction.REJECT));

        assertEquals("REJECTED", result.status());
        assertEquals(LoanApplicationStatus.REJECTED, loanApplicationRepository.savedApplication.status());
        assertEquals(limit(0), limitRepository.savedLimit.reservedAmount());
        assertEquals(limit(6_000_000), limitRepository.savedLimit.availableAmount());
        assertEquals(1, movementRepository.savedMovements.size());
        assertEquals(SalaryAdvanceLimitMovementType.RESERVATION_RELEASED,
                movementRepository.savedMovements.get(0).movementType());
        assertEquals(LOAN_APPLICATION_ID, movementRepository.savedMovements.get(0).loanApplicationId());
        assertEquals(limit(3_000_000), movementRepository.savedMovements.get(0).amount());
    }

    @Test
    void appliesReturnToReviewDecision() {
        LoanApplicationReviewDto result = service.applyApprovalDecision(
                command(LoanApprovalDecisionAction.RETURN_TO_LOAN_OFFICER_REVIEW)
        );

        assertEquals("RETURNED_TO_REVIEW", result.status());
        assertEquals(LoanApplicationStatus.RETURNED_TO_REVIEW, loanApplicationRepository.savedApplication.status());
    }

    @Test
    void rejectsDecisionWhenLoanApplicationIsNotApprovalPending() {
        loanApplicationRepository.application = loanApplication(LoanApplicationStatus.UNDER_REVIEW);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.applyApprovalDecision(command(LoanApprovalDecisionAction.APPROVE))
        );

        assertEquals("APPROVAL_DECISION_NOT_ALLOWED", exception.getErrorCode());
        assertTrue(movementRepository.savedMovements.isEmpty());
    }

    private ApplyApprovalDecisionCommand command(LoanApprovalDecisionAction action) {
        return new ApplyApprovalDecisionCommand(
                LOAN_APPLICATION_ID,
                DECISION_ID,
                RECOMMENDATION_ID,
                APPROVER_USER_ID,
                action,
                LocalDateTime.now()
        );
    }

    private LoanApplication loanApplication(LoanApplicationStatus status) {
        return new LoanApplication(
                LOAN_APPLICATION_ID,
                CUSTOMER_ID,
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "SA-20260630-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                status,
                limit(3_000_000),
                1,
                LocalDateTime.now()
        );
    }

    private BigDecimal limit(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private class FakeLoanApplicationRepository implements LoanApplicationRepository {

        private LoanApplication application = loanApplication(LoanApplicationStatus.APPROVAL_PENDING);
        private LoanApplication savedApplication;

        @Override
        public LoanApplication save(LoanApplication loanApplication) {
            savedApplication = loanApplication;
            application = loanApplication;
            return loanApplication;
        }

        @Override
        public Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId) {
            return Optional.ofNullable(application)
                    .filter(value -> value.id().equals(loanApplicationId));
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

    private class FakeSalaryAdvanceVerificationRepository implements SalaryAdvanceVerificationRepository {

        @Override
        public SalaryAdvanceVerification save(SalaryAdvanceVerification salaryAdvanceVerification) {
            return salaryAdvanceVerification;
        }

        @Override
        public Optional<SalaryAdvanceVerification> findByLoanApplicationId(UUID loanApplicationId) {
            if (!LOAN_APPLICATION_ID.equals(loanApplicationId)) {
                return Optional.empty();
            }

            return Optional.of(new SalaryAdvanceVerification(
                    UUID.randomUUID(),
                    LOAN_APPLICATION_ID,
                    CUSTOMER_ID,
                    LINK_ID,
                    LIMIT_ID,
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01"),
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"),
                    SalaryAdvanceEmployeeVerificationOutcome.MATCHED_ACTIVE,
                    ProductVerificationResult.VERIFIED,
                    limit(6_000_000),
                    limit(0),
                    limit(3_000_000),
                    limit(3_000_000),
                    LocalDateTime.now()
            ));
        }
    }

    private class FakeSalaryAdvanceLimitRepository implements SalaryAdvanceLimitRepository {

        private SalaryAdvanceLimit currentLimit = new SalaryAdvanceLimit(
                LIMIT_ID,
                CUSTOMER_ID,
                LINK_ID,
                limit(6_000_000),
                limit(0),
                limit(3_000_000),
                limit(3_000_000),
                SalaryAdvanceLimitStatus.ACTIVE,
                LocalDateTime.now()
        );
        private SalaryAdvanceLimit savedLimit;

        @Override
        public void acquireCustomerLinkLock(UUID customerId, UUID customerPartnerEmployeeLinkId) {
        }

        @Override
        public Optional<SalaryAdvanceLimit> findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                UUID customerId,
                UUID customerPartnerEmployeeLinkId
        ) {
            return Optional.ofNullable(currentLimit)
                    .filter(value -> value.customerId().equals(customerId))
                    .filter(value -> value.customerPartnerEmployeeLinkId().equals(customerPartnerEmployeeLinkId));
        }

        @Override
        public SalaryAdvanceLimit save(SalaryAdvanceLimit salaryAdvanceLimit) {
            savedLimit = salaryAdvanceLimit;
            currentLimit = salaryAdvanceLimit;
            return salaryAdvanceLimit;
        }
    }

    private static class FakeSalaryAdvanceLimitMovementRepository implements SalaryAdvanceLimitMovementRepository {

        private final List<SalaryAdvanceLimitMovement> savedMovements = new ArrayList<>();

        @Override
        public SalaryAdvanceLimitMovement save(SalaryAdvanceLimitMovement salaryAdvanceLimitMovement) {
            savedMovements.add(salaryAdvanceLimitMovement);
            return salaryAdvanceLimitMovement;
        }
    }
}
