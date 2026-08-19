package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.MeridianPlatformApplication;
import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.dto.CollateralDetailsRequest;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.loan.application.service.ApplyApprovalDecisionService;
import com.meridian.platform.loan.application.service.ApplyReviewRecommendationService;
import com.meridian.platform.loan.application.service.StartLoanApplicationReviewService;
import com.meridian.platform.loan.domain.model.CollateralType;
import com.meridian.platform.loan.domain.model.LoanApprovalDecisionAction;
import com.meridian.platform.loan.domain.model.LoanReviewRecommendationAction;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
        classes = {
                MeridianPlatformApplication.class,
                CollateralLoanOriginationPostgreSqlIntegrationTest.CollateralTestConfiguration.class
        },
        properties = {
                "meridian.loan.offer-expiry.enabled=false",
                "meridian.document.orphan-reconciliation.enabled=false"
        }
)
class CollateralLoanProgressionGuardPostgreSqlIntegrationTest {

    private static final String TEST_SCHEMA = "meridian_collateral_guard_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String STORAGE_ROOT = Path.of(
            "target", "collateral-guard-documents-" + UUID.randomUUID()
    ).toAbsolutePath().toString();

    @Autowired private StartCollateralLoanApplicationUseCase origination;
    @Autowired private StartLoanApplicationReviewService startReview;
    @Autowired private ApplyReviewRecommendationService applyRecommendation;
    @Autowired private ApplyApprovalDecisionService applyApproval;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CollateralLoanOriginationPostgreSqlIntegrationTest.MutableCurrentUserProvider currentUser;

    private UUID customerId;
    private UUID userId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + TEST_SCHEMA);
        registry.add("meridian.document.storage-root", () -> STORAGE_ROOT);
    }

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        userId = UUID.randomUUID();
        createReadyCustomer();
        currentUser.use(userId, customerId);
    }

    @Test
    void pendingVerificationBlocksReviewRecommendationAndApprovalWithoutPartialEffects() {
        CollateralLoanApplicationDto application = origination.startCollateralLoanApplication(request());
        UUID applicationId = application.loanApplicationId();

        jdbcTemplate.update("UPDATE loan_applications SET status = 'SUBMITTED' WHERE id = ?", applicationId);
        assertEquals("PRODUCT_VERIFICATION_PENDING", assertThrows(
                BusinessRuleViolationException.class,
                () -> startReview.startReview(applicationId)
        ).getErrorCode());
        assertStatusAndNoDownstreamEffects(applicationId, "SUBMITTED");

        jdbcTemplate.update("UPDATE loan_applications SET status = 'UNDER_REVIEW' WHERE id = ?", applicationId);
        LocalDateTime recommendationTime = LocalDateTime.parse("2026-08-13T10:00:00");
        assertEquals("PRODUCT_VERIFICATION_PENDING", assertThrows(
                BusinessRuleViolationException.class,
                () -> applyRecommendation.applyReviewRecommendation(new ApplyReviewRecommendationCommand(
                        applicationId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        userId,
                        LoanReviewRecommendationAction.RECOMMEND_APPROVAL,
                        "Synthetic progression guard proof",
                        null,
                        null,
                        recommendationTime,
                        BusinessOperationContext.user(UUID.randomUUID(), userId, recommendationTime)
                ))
        ).getErrorCode());
        assertStatusAndNoDownstreamEffects(applicationId, "UNDER_REVIEW");

        jdbcTemplate.update("UPDATE loan_applications SET status = 'APPROVAL_PENDING' WHERE id = ?", applicationId);
        LocalDateTime decisionTime = LocalDateTime.parse("2026-08-13T10:30:00");
        assertEquals("PRODUCT_APPROVAL_EXECUTION_UNSUPPORTED", assertThrows(
                BusinessStateConflictException.class,
                () -> applyApproval.applyApprovalDecision(new ApplyApprovalDecisionCommand(
                        applicationId,
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        userId,
                        LoanApprovalDecisionAction.APPROVE,
                        "Synthetic progression guard proof",
                        null,
                        null,
                        decisionTime,
                        BusinessOperationContext.user(UUID.randomUUID(), userId, decisionTime)
                ))
        ).getErrorCode());
        assertStatusAndNoDownstreamEffects(applicationId, "APPROVAL_PENDING");
    }

    @Test
    void verificationCycleCannotBeDeletedToBypassReviewGate() {
        CollateralLoanApplicationDto application = origination.startCollateralLoanApplication(request());
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        "DELETE FROM collateral_loan_verifications WHERE loan_application_id = ?",
                        application.loanApplicationId()
                )
        );
        assertEquals(1, count(
                "SELECT count(*) FROM collateral_loan_verifications WHERE loan_application_id = ?",
                application.loanApplicationId()
        ));
    }

    private void assertStatusAndNoDownstreamEffects(UUID applicationId, String expectedStatus) {
        assertEquals(expectedStatus, jdbcTemplate.queryForObject(
                "SELECT status FROM loan_applications WHERE id = ?", String.class, applicationId
        ));
        assertEquals(0, count("SELECT count(*) FROM loan_application_review_cycles "
                + "WHERE loan_application_id = ?", applicationId));
        assertEquals(0, count("SELECT count(*) FROM approved_offers WHERE loan_application_id = ?", applicationId));
        assertEquals(1, count("SELECT count(*) FROM loan_application_status_transitions "
                + "WHERE loan_application_id = ?", applicationId));
    }

    private CollateralLoanApplicationRequest request() {
        return new CollateralLoanApplicationRequest(
                new BigDecimal("25000000"),
                12,
                new CollateralDetailsRequest(
                        CollateralType.CAR,
                        "Customer vehicle",
                        new BigDecimal("50000000"),
                        "Customer-submitted ownership statement",
                        "Normal used condition"
                )
        );
    }

    private void createReadyCustomer() {
        String suffix = customerId.toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO customers "
                        + "(id, customer_number, status, verification_status, profile_completion_status) "
                        + "VALUES (?, ?, 'ACTIVE', 'UNVERIFIED', 'COMPLETE')",
                customerId, "CL-GUARD-" + suffix);
        jdbcTemplate.update("INSERT INTO customer_profiles "
                        + "(id, customer_id, full_name, identity_reference_ciphertext, "
                        + "identity_reference_fingerprint, identity_reference_last_four, phone_number, "
                        + "residential_address, employment_status, employer_name, "
                        + "terms_consent_accepted, data_processing_consent_accepted) "
                        + "VALUES (?, ?, 'Collateral Guard Customer', 'protected-test-value', ?, '1234', "
                        + "'0900000000', 'Test Address', 'EMPLOYED', 'Test Employer', TRUE, TRUE)",
                UUID.randomUUID(), customerId, "identity-" + suffix);
        jdbcTemplate.update("INSERT INTO customer_bank_accounts "
                        + "(id, customer_id, bank_code, bank_name_snapshot, account_holder_name, "
                        + "account_number_ciphertext, account_number_fingerprint, account_number_last_four, "
                        + "status, primary_account) "
                        + "VALUES (?, ?, 'TEST', 'Test Bank', 'Collateral Guard Customer', "
                        + "'protected-test-account', ?, '5678', 'ACTIVE', TRUE)",
                UUID.randomUUID(), customerId, "account-" + suffix);
        jdbcTemplate.update("INSERT INTO users "
                        + "(id, email, normalized_email, password_hash, user_type, status, display_name, customer_id) "
                        + "VALUES (?, ?, ?, 'test-password-hash', 'CUSTOMER', 'ACTIVE', "
                        + "'Collateral Guard Customer', ?)",
                userId, "guard-" + suffix + "@meridian.test", "guard-" + suffix + "@meridian.test", customerId);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }
}
