package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.dto.CreateStaffAssistedCustomerRequest;
import com.meridian.platform.customer.application.port.in.StaffCustomerIntakeUseCase;
import com.meridian.platform.identity.application.dto.EmailVerificationConfirmationRequest;
import com.meridian.platform.identity.application.dto.EmailVerificationRequest;
import com.meridian.platform.identity.application.dto.EnableCustomerDigitalAccessRequest;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.dto.PasswordResetConfirmationRequest;
import com.meridian.platform.identity.application.dto.PasswordResetRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmEmailVerificationUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmPasswordResetUseCase;
import com.meridian.platform.identity.application.port.in.ManageCustomerDigitalAccessUseCase;
import com.meridian.platform.identity.application.port.in.RequestEmailVerificationUseCase;
import com.meridian.platform.identity.application.port.in.RequestPasswordResetUseCase;
import com.meridian.platform.identity.application.port.out.CustomerDigitalAccessInvitationPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationNotificationPort;
import com.meridian.platform.identity.application.port.out.EmailVerificationTokenCodecPort;
import com.meridian.platform.identity.application.port.out.PasswordResetNotificationPort;
import com.meridian.platform.identity.domain.model.UserType;
import com.meridian.platform.loan.application.port.in.QueryLoanApplicationUseCase;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthenticationFailedException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false",
        "meridian.identity.rate-limit.login.max-requests=1000"
})
class CustomerDigitalAccessPostgreSqlIntegrationTest {
    private static final String SCHEMA = "digital_access_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-0000-0000-000000000305");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Autowired StaffCustomerIntakeUseCase intake;
    @Autowired ManageCustomerDigitalAccessUseCase digitalAccess;
    @Autowired ConfirmEmailVerificationUseCase verification;
    @Autowired RequestEmailVerificationUseCase resend;
    @Autowired RequestPasswordResetUseCase passwordReset;
    @Autowired ConfirmPasswordResetUseCase passwordConfirmation;
    @Autowired AuthenticationUseCase authentication;
    @Autowired QueryLoanApplicationUseCase ownApplications;
    @Autowired EmailVerificationTokenCodecPort verificationCodec;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CurrentUserProvider currentUsers;
    @MockitoBean CustomerDigitalAccessInvitationPort invitation;
    @MockitoBean EmailVerificationNotificationPort verificationMail;
    @MockitoBean PasswordResetNotificationPort resetMail;

    private void staffActor() {
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(STAFF_ID,
                "loan.officer@meridian.local", "STAFF", null, Set.of("LOAN_OFFICER"),
                Set.of("customer:intake:manage", "customer:read")));
    }

    private UUID createCustomer(String identity) {
        staffActor();
        return intake.createCustomer(new CreateStaffAssistedCustomerRequest(
                "Existing Customer", identity, "0900000000", "Meridian Street", "EMPLOYED", null,
                false, false)).customerId();
    }

    @Test
    void linksExistingCustomerThroughVerificationPasswordResetAndHistoricalLoanRead() {
        String identity = "ID" + UUID.randomUUID().toString().substring(0, 12);
        UUID customerId = createCustomer(identity);
        UUID applicationId = UUID.randomUUID();
        UUID productId = jdbc.queryForObject("SELECT id FROM loan_products WHERE product_code = 'UNSECURED_CONSUMER_LOAN' LIMIT 1", UUID.class);
        jdbc.update("""
                INSERT INTO loan_applications (id, customer_id, loan_product_id, application_number,
                    product_code, product_type, status, requested_amount, requested_term_months,
                    submitted_at, origination_channel)
                VALUES (?, ?, ?, ?, 'UNSECURED_CONSUMER_LOAN', 'UNSECURED', 'DOCUMENTS_PENDING',
                    10000000, 12, CURRENT_TIMESTAMP, 'STAFF_ASSISTED')
                """, applicationId, customerId, productId, "APP-" + UUID.randomUUID());
        int customersBefore = count("SELECT COUNT(*) FROM customers");
        var customerBefore = jdbc.queryForMap(
                "SELECT customer_number, status, verification_status, profile_completion_status FROM customers WHERE id = ?",
                customerId);
        var profileBefore = jdbc.queryForMap(
                "SELECT full_name, identity_reference_fingerprint FROM customer_profiles WHERE customer_id = ?",
                customerId);
        int bankAccountsBefore = count("SELECT COUNT(*) FROM customer_bank_accounts WHERE customer_id = ?", customerId);
        int loanAccountsBefore = count("SELECT COUNT(*) FROM loan_accounts WHERE customer_id = ?", customerId);
        int repaymentsBefore = count("SELECT COUNT(*) FROM repayment_transactions WHERE loan_account_id IN (SELECT id FROM loan_accounts WHERE customer_id = ?)", customerId);
        String email = "digital-" + UUID.randomUUID() + "@meridian.local";

        assertFalse(digitalAccess.status(customerId).enabled());
        var activated = digitalAccess.enable(customerId, new EnableCustomerDigitalAccessRequest(
                "  " + email.toUpperCase() + " ", identity));
        assertEquals(customerId, activated.customerId());
        assertEquals(email, activated.email());
        assertFalse(activated.emailVerified());
        assertEquals(customersBefore, count("SELECT COUNT(*) FROM customers"));
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE customer_id = ?", customerId));
        assertEquals("INCOMPLETE", jdbc.queryForObject(
                "SELECT profile_completion_status FROM customers WHERE id = ?", String.class, customerId));
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE customer_id = ?", UUID.class, customerId);
        var user = jdbc.queryForMap("SELECT user_type, status, display_name, email_verified_at, authorization_version, failed_login_attempts, locked_until, password_hash FROM users WHERE id = ?", userId);
        assertEquals("CUSTOMER", user.get("user_type"));
        assertEquals("ACTIVE", user.get("status"));
        assertEquals("Existing Customer", user.get("display_name"));
        assertNull(user.get("email_verified_at"));
        assertEquals(0L, ((Number) user.get("authorization_version")).longValue());
        assertEquals(0, ((Number) user.get("failed_login_attempts")).intValue());
        assertNull(user.get("locked_until"));
        assertFalse(passwordEncoder.matches("Meridian@123", (String) user.get("password_hash")));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM role_assignments ra JOIN roles r ON r.id = ra.role_id
                WHERE ra.user_id = ? AND r.code = 'CUSTOMER'
                """, userId));
        assertEquals(1, count("SELECT COUNT(*) FROM role_assignments WHERE user_id = ?", userId));
        assertThrows(AuthenticationFailedException.class,
                () -> authentication.login(new LoginRequest(email, "Meridian@123", UserType.CUSTOMER)));

        var invitationToken = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(invitation).sendInvitation(eq(email), invitationToken.capture());
        String rawToken = invitationToken.getValue();
        String digest = jdbc.queryForObject("SELECT token_digest FROM email_verification_tokens WHERE user_id = ?", String.class, userId);
        assertEquals(verificationCodec.digest(rawToken), digest);
        assertNotEquals(rawToken, digest);
        var audit = jdbc.queryForMap("SELECT action, actor_user_id, payload::text AS payload FROM audit_events WHERE entity_id = ? AND action = 'IDENTITY_CUSTOMER_DIGITAL_ACCESS_ENABLED'", userId);
        assertEquals(STAFF_ID, audit.get("actor_user_id"));
        assertTrue(((String) audit.get("payload")).contains(customerId.toString()));
        assertFalse(((String) audit.get("payload")).contains(email));
        assertFalse(((String) audit.get("payload")).contains(identity));
        assertFalse(((String) audit.get("payload")).contains(rawToken));

        resend.requestVerification(new EmailVerificationRequest(email));
        var resent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(verificationMail).sendVerificationEmail(eq(email), resent.capture());
        verification.confirmVerification(new EmailVerificationConfirmationRequest(resent.getValue()));
        assertTrue(digitalAccess.status(customerId).emailVerified());
        passwordReset.requestReset(new PasswordResetRequest(email));
        var resetToken = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(resetMail).sendPasswordResetEmail(eq(email), resetToken.capture());
        passwordConfirmation.confirmReset(new PasswordResetConfirmationRequest(resetToken.getValue(), "Chosen-Customer-Password-123"));
        var loggedIn = authentication.login(new LoginRequest(email, "Chosen-Customer-Password-123", UserType.CUSTOMER));
        assertEquals(customerId, loggedIn.response().customerId());
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(loggedIn.response().userId(),
                email, "CUSTOMER", customerId, Set.of("CUSTOMER"), Set.of("loan:read:own")));
        var application = ownApplications.queryOwnApplications().stream()
                .filter(item -> item.loanApplicationId().equals(applicationId)).findFirst().orElseThrow();
        assertEquals("STAFF_ASSISTED", application.originationChannel());
        assertEquals("NONE", application.requiredAction().name());
        assertEquals("STAFF_ASSISTED", jdbc.queryForObject(
                "SELECT origination_channel FROM loan_applications WHERE id = ?", String.class, applicationId));
        assertEquals(customersBefore, count("SELECT COUNT(*) FROM customers"));
        assertEquals(customerBefore, jdbc.queryForMap(
                "SELECT customer_number, status, verification_status, profile_completion_status FROM customers WHERE id = ?",
                customerId));
        assertEquals(profileBefore, jdbc.queryForMap(
                "SELECT full_name, identity_reference_fingerprint FROM customer_profiles WHERE customer_id = ?",
                customerId));
        assertEquals(bankAccountsBefore, count("SELECT COUNT(*) FROM customer_bank_accounts WHERE customer_id = ?", customerId));
        assertEquals(loanAccountsBefore, count("SELECT COUNT(*) FROM loan_accounts WHERE customer_id = ?", customerId));
        assertEquals(repaymentsBefore, count("SELECT COUNT(*) FROM repayment_transactions WHERE loan_account_id IN (SELECT id FROM loan_accounts WHERE customer_id = ?)", customerId));
    }

    @Test
    void mismatchConflictsAndMailFailurePreserveDurableState() {
        String identity = "ID" + UUID.randomUUID().toString().substring(0, 12);
        UUID customerId = createCustomer(identity);
        String email = "digital-" + UUID.randomUUID() + "@meridian.local";
        assertEquals("CUSTOMER_DIGITAL_ACCESS_OWNERSHIP_NOT_VERIFIED", assertThrows(
                BusinessRuleViolationException.class,
                () -> digitalAccess.enable(customerId, new EnableCustomerDigitalAccessRequest(email, "wrong-reference")))
                .getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE customer_id = ?", customerId));
        doThrow(new IllegalStateException("mail unavailable")).when(invitation).sendInvitation(anyString(), anyString());
        digitalAccess.enable(customerId, new EnableCustomerDigitalAccessRequest(email, identity));
        assertTrue(digitalAccess.status(customerId).enabled());
        assertEquals("CUSTOMER_DIGITAL_ACCESS_ALREADY_ENABLED", assertThrows(BusinessStateConflictException.class,
                () -> digitalAccess.enable(customerId, new EnableCustomerDigitalAccessRequest(
                        "other-" + UUID.randomUUID() + "@meridian.local", identity))).getErrorCode());
        String otherIdentity = "ID" + UUID.randomUUID().toString().substring(0, 12);
        UUID other = createCustomer(otherIdentity);
        assertEquals("EMAIL_ALREADY_REGISTERED", assertThrows(BusinessStateConflictException.class,
                () -> digitalAccess.enable(other, new EnableCustomerDigitalAccessRequest(email, otherIdentity)))
                .getErrorCode());
        String staffEmail = "staff-" + UUID.randomUUID() + "@meridian.local";
        jdbc.update("""
                INSERT INTO users (id, email, normalized_email, password_hash, user_type, status, display_name)
                VALUES (?, ?, ?, ?, 'STAFF', 'ACTIVE', 'Existing Staff')
                """, UUID.randomUUID(), staffEmail, staffEmail, passwordEncoder.encode("unused-staff-password"));
        assertEquals("EMAIL_ALREADY_REGISTERED", assertThrows(BusinessStateConflictException.class,
                () -> digitalAccess.enable(other, new EnableCustomerDigitalAccessRequest(staffEmail, otherIdentity)))
                .getErrorCode());
    }

    @Test
    void concurrentDifferentEmailsCannotCreateTwoUsersForOneCustomer() throws Exception {
        String identity = "ID" + UUID.randomUUID().toString().substring(0, 12);
        UUID customerId = createCustomer(identity);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> enableAfter(start, customerId, identity, "one-" + UUID.randomUUID() + "@meridian.local"));
            var two = pool.submit(() -> enableAfter(start, customerId, identity, "two-" + UUID.randomUUID() + "@meridian.local"));
            start.countDown();
            var results = Set.of(one.get(), two.get());
            assertEquals(Set.of("ENABLED", "CUSTOMER_DIGITAL_ACCESS_ALREADY_ENABLED"), results);
        }
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE customer_id = ?", customerId));
    }

    @Test
    void concurrentSameEmailForDifferentCustomersCreatesOnlyOneUser() throws Exception {
        String firstIdentity = "ID" + UUID.randomUUID().toString().substring(0, 12);
        String secondIdentity = "ID" + UUID.randomUUID().toString().substring(0, 12);
        UUID firstCustomer = createCustomer(firstIdentity);
        UUID secondCustomer = createCustomer(secondIdentity);
        String email = "shared-" + UUID.randomUUID() + "@meridian.local";
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> enableAfter(start, firstCustomer, firstIdentity, email));
            var two = pool.submit(() -> enableAfter(start, secondCustomer, secondIdentity, email));
            start.countDown();
            assertEquals(Set.of("ENABLED", "EMAIL_ALREADY_REGISTERED"), Set.of(one.get(), two.get()));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE normalized_email = ?", email));
    }

    private String enableAfter(CountDownLatch start, UUID customerId, String identity, String email) {
        try {
            start.await();
            digitalAccess.enable(customerId, new EnableCustomerDigitalAccessRequest(email, identity));
            return "ENABLED";
        } catch (BusinessStateConflictException conflict) {
            return conflict.getErrorCode();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private int count(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Integer.class, parameters);
    }
}
