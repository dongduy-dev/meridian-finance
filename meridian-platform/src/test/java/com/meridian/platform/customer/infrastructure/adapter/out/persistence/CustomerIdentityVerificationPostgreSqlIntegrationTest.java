package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.application.service.CustomerIdentityVerificationService;
import com.meridian.platform.customer.application.service.UpdateOwnCustomerProfileService;

import com.meridian.platform.customer.application.dto.*;
import com.meridian.platform.customer.application.port.in.QueryCustomerReadinessUseCase;
import com.meridian.platform.customer.domain.model.CustomerIdentityVerification.RejectionReason;
import com.meridian.platform.document.application.dto.UploadIntakeEvidenceCommand;
import com.meridian.platform.document.application.service.IntakeEvidenceService;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.loan.application.dto.CreateAssistedOriginationCaseRequest;
import com.meridian.platform.loan.application.service.AssistedOriginationService;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.domain.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false", "meridian.document.orphan-reconciliation.enabled=false"})
class CustomerIdentityVerificationPostgreSqlIntegrationTest {
    private static final String SCHEMA = "meridian_identity_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID CUSTOMER_USER = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID OFFICER = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final Set<String> OWN = Set.of("customer:profile:write:own", "customer:identity:write:own", "customer:identity:read:own");
    private static final Set<String> REVIEW = Set.of("customer:identity:verify", "loan:originate:staff", "document:upload:intake");
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired com.meridian.platform.document.application.port.out.DocumentStoragePort storage;
    @Autowired com.meridian.platform.document.application.port.out.DocumentRepository documentReferences;
    @Autowired com.meridian.platform.customer.application.service.CorrectCustomerIdentityReferenceService corrections;
    @Autowired CustomerIdentityVerificationService service;
    @Autowired UpdateOwnCustomerProfileService profiles;
    @Autowired QueryCustomerReadinessUseCase readiness;
    @Autowired AssistedOriginationService cases;
    @Autowired IntakeEvidenceService intake;
    @Autowired com.meridian.platform.customer.application.service.StaffCustomerIntakeService staffProfiles;
    private UUID customerId;
    private String reference;
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.flyway.schemas", () -> SCHEMA); r.add("spring.flyway.default-schema", () -> SCHEMA);
        r.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }
    @BeforeEach void setup() {
        customerId = UUID.randomUUID(); reference = "IDREF-" + customerId;
        jdbc.update("insert into customers (id, customer_number, status, verification_status, profile_completion_status, created_at, updated_at) values (?, ?, 'ACTIVE', 'UNVERIFIED', 'INCOMPLETE', now(), now())", customerId, "CUS-" + customerId);
        customer(); profiles.updateOwnProfile(profile("Ari Fictional", reference, "0900000000"));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private UpdateCustomerProfileRequest profile(String name, String id, String phone) { return new UpdateCustomerProfileRequest(name, id, phone, "Fictional address", "EMPLOYED", "Fictional employer", true, true); }
    private static ByteArrayInputStream pdf(String text) { return new ByteArrayInputStream(("%PDF-1.4\n" + text + "\n%%EOF").getBytes(StandardCharsets.UTF_8)); }
    private void auth(String type, UUID owner, UUID user, Set<String> permissions) {
        var actor = new AuthenticatedUser(user, "fictional@meridian.local", type, owner, Set.of(type.equals("CUSTOMER") ? "CUSTOMER" : "LOAN_OFFICER"), permissions);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of())); SecurityContextHolder.setContext(context);
    }
    private void customer() { auth("CUSTOMER", customerId, CUSTOMER_USER, OWN); }
    private void staff() { auth("STAFF", null, OFFICER, REVIEW); }
    private CustomerIdentityVerificationDto upload() { customer(); return service.submitOwn(UUID.randomUUID(), null, pdf("identity evidence"), "application/pdf", "identity.pdf"); }
    private CustomerIdentityDecisionRequest verifyRequest(CustomerIdentityVerificationDto v) { return new CustomerIdentityDecisionRequest(UUID.randomUUID(), v.evidence().versionId(), reference, null); }
    @Test void correctReferenceAndRetrySamePendingEvidenceWithoutRewritingHistory() throws Exception {
        var v = upload(); String replacement = "CORRECTED-" + customerId;
        var request = new CustomerIdentityDecisionRequest(UUID.randomUUID(), v.evidence().versionId(), replacement, null);
        staff();
        assertEquals("IDENTITY_REFERENCE_MISMATCH", assertThrows(BusinessRuleViolationException.class, () -> service.decide(v.verificationId(), true, request)).getErrorCode());
        String before = jdbc.queryForObject("select row_to_json(v)::text from customer_identity_verifications v where id=?", String.class, v.verificationId());
        customer(); var result = corrections.correctOwn(new CorrectIdentityReferenceRequest(replacement));
        assertEquals("COMPLETE", result.profileCompletionStatus()); assertEquals("UNVERIFIED", result.verificationStatus());
        assertFalse(result.toString().contains(replacement));
        assertEquals(before, jdbc.queryForObject("select row_to_json(v)::text from customer_identity_verifications v where id=?", String.class, v.verificationId()));
        assertEquals(v.evidence().versionId(), service.ownHistory().getFirst().evidence().versionId());
        staff(); assertEquals("VERIFIED", service.decide(v.verificationId(), true, request).status());
        customer(); assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class, () -> corrections.correctOwn(new CorrectIdentityReferenceRequest("OTHER"))).getErrorCode());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action='CUSTOMER_IDENTITY_REFERENCE_CORRECTED' and entity_id=?", Integer.class, customerId));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events where payload::text like ?", Integer.class, "%" + replacement + "%"));
    }
    @Test void rejectedVerifiedNameChangePreservesTerminalVerificationAndHistoricalLoanProvenance() {
        var v = upload(); staff(); service.decide(v.verificationId(), true, verifyRequest(v));
        UUID loan = insertLoan(ProductCode.UNSECURED_CONSUMER_LOAN, customerId, v.verificationId());
        String terminal = jdbc.queryForObject("select row_to_json(v)::text from customer_identity_verifications v where id=?", String.class, v.verificationId());
        String application = jdbc.queryForObject("select row_to_json(l)::text from loan_applications l where id=?", String.class, loan);
        var before = readiness.findReadinessByCustomerId(customerId).orElseThrow();
        String profileBefore = jdbc.queryForObject("select row_to_json(p)::text from customer_profiles p where customer_id=?", String.class, customerId);
        String customerBefore = jdbc.queryForObject("select row_to_json(c)::text from customers c where id=?", String.class, customerId);
        int auditCount = jdbc.queryForObject("select count(*) from audit_events where entity_id=?", Integer.class, customerId);
        customer();
        assertEquals("VERIFIED_IDENTITY_CHANGE_NOT_ALLOWED", assertThrows(BusinessStateConflictException.class,
                () -> profiles.updateOwnProfile(profile("Changed Fictional Name", null, "0900000000"))).getErrorCode());
        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class,
                () -> corrections.correctOwn(new CorrectIdentityReferenceRequest("CORRECTED-" + customerId))).getErrorCode());
        assertEquals(before, readiness.findReadinessByCustomerId(customerId).orElseThrow());
        assertEquals(v.verificationId(), before.identityVerificationId());
        assertEquals(profileBefore, jdbc.queryForObject("select row_to_json(p)::text from customer_profiles p where customer_id=?", String.class, customerId));
        assertEquals(customerBefore, jdbc.queryForObject("select row_to_json(c)::text from customers c where id=?", String.class, customerId));
        assertEquals(terminal, jdbc.queryForObject("select row_to_json(v)::text from customer_identity_verifications v where id=?", String.class, v.verificationId()));
        assertEquals(application, jdbc.queryForObject("select row_to_json(l)::text from loan_applications l where id=?", String.class, loan));
        assertEquals(auditCount, jdbc.queryForObject("select count(*) from audit_events where entity_id=?", Integer.class, customerId));
    }
    @Test void verifiedIdentityCannotBeChangedByMakingTheProfileIncompleteFirst() {
        var v = upload(); staff(); service.decide(v.verificationId(), true, verifyRequest(v));
        customer();
        profiles.updateOwnProfile(new UpdateCustomerProfileRequest("Ari Fictional", null, "0900000000",
                "Fictional address", "EMPLOYED", "Fictional employer", false, true));
        assertEquals("INCOMPLETE", jdbc.queryForObject("select profile_completion_status from customers where id=?", String.class, customerId));
        String fingerprint = jdbc.queryForObject("select identity_reference_fingerprint from customer_profiles where customer_id=?", String.class, customerId);
        var changed = profile("Ari Fictional", "DIFFERENT-" + customerId, "0900000000");
        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class,
                () -> profiles.updateOwnProfile(changed)).getErrorCode());
        auth("STAFF", null, OFFICER, Set.of("customer:intake:manage"));
        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class,
                () -> staffProfiles.updateProfile(customerId, changed)).getErrorCode());
        assertEquals(fingerprint, jdbc.queryForObject("select identity_reference_fingerprint from customer_profiles where customer_id=?", String.class, customerId));
        assertEquals("VERIFIED", jdbc.queryForObject("select verification_status from customers where id=?", String.class, customerId));
        assertEquals("VERIFIED", jdbc.queryForObject("select status from customer_identity_verifications where id=?", String.class, v.verificationId()));
    }
    @Test void staffCorrectionUsesIntakeAuthorityAndKeepsPendingDocument() {
        var v = upload(); auth("STAFF", null, OFFICER, Set.of("customer:intake:manage"));
        corrections.correctForIntake(customerId, new CorrectIdentityReferenceRequest("STAFF-CORRECTED-" + customerId));
        staff(); assertEquals("PENDING_REVIEW", service.detail(v.verificationId()).status());
        assertEquals(v.evidence().versionId(), service.detail(v.verificationId()).evidence().versionId());
    }
    @Test void competingDuplicateCorrectionsCommitOnlyOneCustomerAndAudit() throws Exception {
        UUID second = UUID.randomUUID();
        jdbc.update("insert into customers (id,customer_number,status,verification_status,profile_completion_status) values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')", second, "CUS-" + second);
        auth("CUSTOMER", second, CUSTOMER_USER, OWN); profiles.updateOwnProfile(profile("Second Fictional", "SECOND-" + second, "0900000000"));
        String shared = "SHARED-" + UUID.randomUUID(); var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<Boolean>>();
            for (UUID owner : List.of(customerId, second)) tasks.add(executor.submit(() -> {
                auth("CUSTOMER", owner, CUSTOMER_USER, OWN); latch.await();
                try { corrections.correctOwn(new CorrectIdentityReferenceRequest(shared)); return true; }
                catch (BusinessStateConflictException expected) { assertEquals("IDENTITY_REFERENCE_ALREADY_IN_USE", expected.getErrorCode()); return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            latch.countDown(); int successes=0; for(var task:tasks) if(task.get(30,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action='CUSTOMER_IDENTITY_REFERENCE_CORRECTED' and entity_id in (?,?)", Integer.class, customerId, second));
    }
    @Test void concurrentCorrectionAndVerificationCannotVerifyTheWrongCurrentIdentity() throws Exception {
        var v = upload(); String replacement = "NEW-" + customerId; var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var review = executor.submit(() -> { staff(); latch.await(); try { service.decide(v.verificationId(), true, verifyRequest(v)); return true; }
                catch (BusinessRuleViolationException expected) { assertEquals("IDENTITY_REFERENCE_MISMATCH", expected.getErrorCode()); return false; }
                finally { SecurityContextHolder.clearContext(); } });
            var correction = executor.submit(() -> { customer(); latch.await(); try { corrections.correctOwn(new CorrectIdentityReferenceRequest(replacement)); return true; }
                catch (BusinessStateConflictException expected) { assertEquals("IDENTITY_REFERENCE_IMMUTABLE", expected.getErrorCode()); return false; }
                finally { SecurityContextHolder.clearContext(); } });
            latch.countDown(); boolean reviewed=review.get(30,TimeUnit.SECONDS), corrected=correction.get(30,TimeUnit.SECONDS);
            assertNotEquals(reviewed,corrected);
            assertEquals(reviewed ? "VERIFIED" : "UNVERIFIED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        }
    }
    @Test void v71CleanAndUpgradePreserveAuditActionsAndDoNotChangePermissions() throws Exception {
        String clean="correction_clean_"+UUID.randomUUID().toString().replace("-", "");
        String upgrade="correction_upgrade_"+UUID.randomUUID().toString().replace("-", "");
        try {
            var migrated=org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(clean).defaultSchema(clean).locations("classpath:db/migration").load();
            migrated.migrate(); assertEquals("71", migrated.info().current().getVersion().toString()); migrated.validate();
            var previous=org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(upgrade).defaultSchema(upgrade).locations("classpath:db/migration").target("70").load(); previous.migrate();
            int permissions=jdbc.queryForObject("select count(*) from "+upgrade+".role_permissions", Integer.class);
            var current=org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(upgrade).defaultSchema(upgrade).locations("classpath:db/migration").load();
            assertEquals(1,current.migrate().migrationsExecuted); assertEquals(0,current.migrate().migrationsExecuted); current.validate();
            assertEquals(permissions,jdbc.queryForObject("select count(*) from "+upgrade+".role_permissions", Integer.class));
            for(String schema:List.of(clean,upgrade)) {
                String constraint=jdbc.queryForObject("select pg_get_constraintdef(c.oid) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname=? and c.conname='chk_audit_events_action'", String.class,schema);
                for(var action:com.meridian.platform.shared.domain.audit.BusinessAuditAction.values()) assertTrue(constraint.contains("'"+action.name()+"'"),action.name());
            }
        } finally { jdbc.execute("drop schema if exists "+clean+" cascade"); jdbc.execute("drop schema if exists "+upgrade+" cascade"); }
    }
    @Test void profileAndUploadRemainUnverifiedAndBankIsNotRequired() {
        assertEquals("UNVERIFIED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        var v = upload(); assertEquals("PENDING_REVIEW", v.status());
        assertNull(readiness.findReadinessByCustomerId(customerId).orElseThrow().identityVerificationId());
        assertFalse(readiness.findReadinessByCustomerId(customerId).orElseThrow().hasPrimaryActiveBankAccount());
    }
    @Test void verifyRequiresMatchAndProducesSafeReusableReadinessWithExactReplay() {
        var v = upload(); staff();
        var wrong = new CustomerIdentityDecisionRequest(UUID.randomUUID(), v.evidence().versionId(), "WRONG-REFERENCE", null);
        assertEquals("IDENTITY_REFERENCE_MISMATCH", assertThrows(BusinessRuleViolationException.class, () -> service.decide(v.verificationId(), true, wrong)).getErrorCode());
        assertEquals("PENDING_REVIEW", service.detail(v.verificationId()).status());
        var request = verifyRequest(v); var done = service.decide(v.verificationId(), true, request);
        int count = jdbc.queryForObject("select count(*) from audit_events where action = 'CUSTOMER_IDENTITY_VERIFIED'", Integer.class);
        assertEquals(done, service.decide(v.verificationId(), true, request));
        assertEquals(count, jdbc.queryForObject("select count(*) from audit_events where action = 'CUSTOMER_IDENTITY_VERIFIED'", Integer.class));
        assertEquals(v.verificationId(), readiness.findReadinessByCustomerId(customerId).orElseThrow().identityVerificationId());
        assertFalse(done.toString().contains(reference));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events where payload::text like ?", Integer.class, "%" + reference + "%"));
        assertFalse(request.toString().contains(reference));
        assertThrows(BusinessStateConflictException.class, () -> service.decide(v.verificationId(), false, new CustomerIdentityDecisionRequest(UUID.randomUUID(), v.evidence().versionId(), null, RejectionReason.NAME_MISMATCH)));
    }
    @Test void rejectionAndReplacementPreserveTerminalHistoryAndVersionBytes() {
        var first = upload(); staff(); service.decide(first.verificationId(), false, new CustomerIdentityDecisionRequest(UUID.randomUUID(), first.evidence().versionId(), null, RejectionReason.UNREADABLE_EVIDENCE));
        assertEquals("REJECTED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        customer(); var second = service.submitOwn(UUID.randomUUID(), first.evidence().versionId(), pdf("replacement"), "application/pdf", "replacement.pdf");
        assertEquals(2, second.sequence()); assertEquals("PENDING_REVIEW", second.status());
        assertEquals("UNVERIFIED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        assertEquals("REJECTED", service.ownHistory().get(1).status());
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update customer_identity_verifications set status = 'VERIFIED' where id = ?", first.verificationId()));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update customer_identity_document_versions set original_filename = 'changed.pdf' where id = ?", first.evidence().versionId()));
    }
    @Test void uploadReplayConflictsAreAtomicAndPendingReplacementCannotBeVerified() {
        UUID request = UUID.randomUUID(); customer();
        var first = service.submitOwn(request, null, pdf("first"), "application/pdf", "first.pdf");
        assertEquals(first, service.submitOwn(request, null, pdf("first"), "application/pdf", "first.pdf"));
        assertThrows(BusinessStateConflictException.class, () -> service.submitOwn(request, null, pdf("different"), "application/pdf", "first.pdf"));
        var second = service.submitOwn(UUID.randomUUID(), first.evidence().versionId(), pdf("second"), "application/pdf", "second.pdf");
        assertEquals(2, service.ownHistory().size()); assertEquals("SUPERSEDED", service.ownHistory().get(1).status());
        staff(); assertThrows(BusinessStateConflictException.class, () -> service.decide(first.verificationId(), true, verifyRequest(first)));
        assertEquals("PENDING_REVIEW", service.detail(second.verificationId()).status());
    }
    @Test void verifiedNameIsImmutableWhileContactAndEmploymentPreserveReadiness() {
        var v = upload(); staff(); service.decide(v.verificationId(), true, verifyRequest(v)); customer();
        var before = readiness.findReadinessByCustomerId(customerId).orElseThrow();
        var updated = profiles.updateOwnProfile(new UpdateCustomerProfileRequest(null, null, "0911111111",
                "Changed fictional address", "SELF_EMPLOYED", "Changed fictional employer", true, true));
        assertEquals("Ari Fictional", updated.profile().fullName());
        assertEquals("0911111111", updated.profile().phoneNumber());
        assertEquals("Changed fictional address", updated.profile().residentialAddress());
        assertEquals("SELF_EMPLOYED", updated.profile().employmentStatus());
        assertEquals("Changed fictional employer", updated.profile().employerName());
        assertEquals(before, readiness.findReadinessByCustomerId(customerId).orElseThrow());
        assertEquals("VERIFIED_IDENTITY_CHANGE_NOT_ALLOWED", assertThrows(BusinessStateConflictException.class,
                () -> profiles.updateOwnProfile(profile("Ari Changed", null, "0911111111"))).getErrorCode());
        assertEquals(before, readiness.findReadinessByCustomerId(customerId).orElseThrow());
        assertEquals("VERIFIED", service.ownHistory().getFirst().status());
        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class,
                () -> profiles.updateOwnProfile(profile("Ari Fictional", "DIFFERENT-REFERENCE", "0911111111"))).getErrorCode());
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events where action='CUSTOMER_IDENTITY_VERIFICATION_INVALIDATED' and entity_id=?", Integer.class, customerId));
    }
    @Test void pendingNameChangeCannotVerifyOldIdentityContext() {
        var v = upload(); customer(); profiles.updateOwnProfile(profile("New Fictional Name", null, "0911111111")); staff();
        assertEquals("IDENTITY_VERIFICATION_EVIDENCE_STALE", assertThrows(BusinessStateConflictException.class, () -> service.decide(v.verificationId(), true, verifyRequest(v))).getErrorCode());
    }
    @Test void competingReviewersProduceExactlyOneTerminalDecision() throws Exception {
        var v = upload(); var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) tasks.add(executor.submit(() -> {
                staff(); latch.await(); try { service.decide(v.verificationId(), true, verifyRequest(v)); return true; }
                catch (BusinessStateConflictException expected) { return false; } finally { SecurityContextHolder.clearContext(); }
            }));
            latch.countDown(); int successes = 0; for (var task : tasks) if (task.get(30, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_identity_verifications where customer_id = ? and status = 'VERIFIED'", Integer.class, customerId));
    }
    @Test void ownConcealmentAndGenericStaffPermissionsFailClosedBeforeContent() throws Exception {
        var v = upload(); customer(); try (var stream = service.readOwn(v.verificationId()).content()) { assertTrue(stream.readAllBytes().length > 0); }
        auth("CUSTOMER", UUID.randomUUID(), CUSTOMER_USER, OWN);
        assertThrows(EntityNotFoundException.class, () -> service.readOwn(v.verificationId()));
        for (String permission : List.of("customer:read", "document:review", "loan:read", "partner:manage", "approval:decide", "loan:disburse")) {
            auth("STAFF", null, OFFICER, Set.of(permission)); assertThrows(AuthorizationException.class, () -> service.pending(0, 25)); assertThrows(AuthorizationException.class, () -> service.readStaff(v.verificationId()));
        }
        staff(); try (var stream = service.readStaff(v.verificationId()).content()) { assertTrue(stream.readAllBytes().length > 0); }
    }
    @Test void intakeBothProductsBindExactExistingBytesAndStaleOrWrongEvidenceFails() {
        for (ProductCode product : List.of(ProductCode.UNSECURED_CONSUMER_LOAN, ProductCode.COLLATERAL_LOAN)) {
            staff(); var c = cases.createCase(new CreateAssistedOriginationCaseRequest(product, customerId));
            var uploaded = intake.upload(new UploadIntakeEvidenceCommand(c.assistedOriginationCaseId(), IntakeEvidenceType.CUSTOMER_IDENTITY, UUID.randomUUID(), null, "intake.pdf", "application/pdf", pdf("intake")));
            var v = service.submitIntake(c.assistedOriginationCaseId(), uploaded.intakeDocumentVersionId());
            assertEquals("STAFF_ASSISTED_INTAKE", v.source());
            assertTrue(documentReferences.existsStorageReference(jdbc.queryForObject("select storage_key from intake_document_versions where id=?", String.class, uploaded.intakeDocumentVersionId())));
            assertEquals(0, jdbc.queryForObject("select count(*) from customer_identity_documents where customer_id = ?", Integer.class, customerId));
            var replacement = intake.upload(new UploadIntakeEvidenceCommand(c.assistedOriginationCaseId(), IntakeEvidenceType.CUSTOMER_IDENTITY, UUID.randomUUID(), uploaded.intakeDocumentVersionId(), "replacement.pdf", "application/pdf", pdf("replacement")));
            assertEquals("IDENTITY_VERIFICATION_EVIDENCE_STALE", assertThrows(BusinessStateConflictException.class, () -> service.decide(v.verificationId(), true, verifyRequest(v))).getErrorCode());
            var current = service.submitIntake(c.assistedOriginationCaseId(), replacement.intakeDocumentVersionId());
            service.decide(current.verificationId(), false, new CustomerIdentityDecisionRequest(UUID.randomUUID(), current.evidence().versionId(), null, RejectionReason.UNACCEPTABLE_EVIDENCE));
            assertThrows(BusinessStateConflictException.class, () -> service.submitIntake(c.assistedOriginationCaseId(), UUID.randomUUID()));
        }
    }
    @Test void unsafeMimeAndFilenameDoNotProduceAttempts() {
        customer();
        assertThrows(BusinessRuleViolationException.class, () -> service.submitOwn(UUID.randomUUID(), null, pdf("bad mime"), "image/png", "identity.png"));
        assertThrows(BusinessRuleViolationException.class, () -> service.submitOwn(UUID.randomUUID(), null, pdf("bad filename"), "application/pdf", "../identity.pdf"));
        assertTrue(service.ownHistory().isEmpty());
    }
    @Test void staffCannotChangeVerifiedNameWhileOtherMutableFactsPreserveVerification() {
        var v = upload(); staff(); service.decide(v.verificationId(), true, verifyRequest(v));
        var before = readiness.findReadinessByCustomerId(customerId).orElseThrow();
        auth("STAFF", null, OFFICER, Set.of("customer:intake:manage"));
        var updated = staffProfiles.updateProfile(customerId, new UpdateCustomerProfileRequest(null, null,
                "0922222222", "Changed fictional address", "SELF_EMPLOYED", "Changed employer", true, true));
        assertEquals("Ari Fictional", updated.profile().fullName());
        assertEquals("0922222222", updated.profile().phoneNumber());
        assertEquals("Changed fictional address", updated.profile().residentialAddress());
        assertEquals("SELF_EMPLOYED", updated.profile().employmentStatus());
        assertEquals("Changed employer", updated.profile().employerName());
        var profileBefore = jdbc.queryForObject("select row_to_json(p)::text from customer_profiles p where customer_id=?", String.class, customerId);
        int auditCount = jdbc.queryForObject("select count(*) from audit_events where entity_id=?", Integer.class, customerId);
        assertEquals("VERIFIED_IDENTITY_CHANGE_NOT_ALLOWED", assertThrows(BusinessStateConflictException.class,
                () -> staffProfiles.updateProfile(customerId, profile("Ari Staff Changed", null, "0922222222"))).getErrorCode());
        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", assertThrows(BusinessStateConflictException.class,
                () -> corrections.correctForIntake(customerId, new CorrectIdentityReferenceRequest("STAFF-CORRECTED-" + customerId))).getErrorCode());
        assertEquals(before, readiness.findReadinessByCustomerId(customerId).orElseThrow());
        assertEquals(profileBefore, jdbc.queryForObject("select row_to_json(p)::text from customer_profiles p where customer_id=?", String.class, customerId));
        assertEquals(auditCount, jdbc.queryForObject("select count(*) from audit_events where entity_id=?", Integer.class, customerId));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events where action='CUSTOMER_IDENTITY_VERIFICATION_INVALIDATED' and entity_id=?", Integer.class, customerId));
    }
    @Test void crossCaseAndPaperEvidenceAreRejectedWithoutCreatingVerification() {
        staff(); var first = cases.createCase(new CreateAssistedOriginationCaseRequest(ProductCode.UNSECURED_CONSUMER_LOAN, customerId));
        var second = cases.createCase(new CreateAssistedOriginationCaseRequest(ProductCode.COLLATERAL_LOAN, customerId));
        var identity = intake.upload(new UploadIntakeEvidenceCommand(first.assistedOriginationCaseId(), IntakeEvidenceType.CUSTOMER_IDENTITY, UUID.randomUUID(), null, "identity.pdf", "application/pdf", pdf("identity")));
        var paper = intake.upload(new UploadIntakeEvidenceCommand(second.assistedOriginationCaseId(), IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION, UUID.randomUUID(), null, "paper.pdf", "application/pdf", pdf("paper")));
        assertThrows(EntityNotFoundException.class, () -> service.submitIntake(second.assistedOriginationCaseId(), identity.intakeDocumentVersionId()));
        assertThrows(EntityNotFoundException.class, () -> service.submitIntake(second.assistedOriginationCaseId(), paper.intakeDocumentVersionId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_identity_verifications where customer_id = ?", Integer.class, customerId));
    }
    @Test void concurrentIdenticalUploadsProduceOneVersionAndAttempt() throws Exception {
        UUID requestId = UUID.randomUUID(); var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<CustomerIdentityVerificationDto> command = () -> { customer(); latch.await(); try {
                return service.submitOwn(requestId, null, pdf("same"), "application/pdf", "identity.pdf");
            } finally { SecurityContextHolder.clearContext(); } };
            var first = executor.submit(command); var second = executor.submit(command); latch.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_identity_verifications where customer_id = ?", Integer.class, customerId));
    }
    @Test void concurrentCriticalProfileChangeNeverLeavesVerifiedStaleIdentity() throws Exception {
        var v = upload(); var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var review = executor.submit(() -> { staff(); latch.await(); try { service.decide(v.verificationId(), true, verifyRequest(v)); return true; }
                catch (BusinessStateConflictException expected) { assertEquals("IDENTITY_VERIFICATION_EVIDENCE_STALE", expected.getErrorCode()); return false; }
                finally { SecurityContextHolder.clearContext(); } });
            var update = executor.submit(() -> { customer(); latch.await(); try { profiles.updateOwnProfile(profile("Ari Concurrent", null, "0911111111")); return true; }
                catch (BusinessStateConflictException expected) { assertEquals("VERIFIED_IDENTITY_CHANGE_NOT_ALLOWED", expected.getErrorCode()); return false; }
                finally { SecurityContextHolder.clearContext(); } });
            latch.countDown(); boolean reviewed = review.get(30, TimeUnit.SECONDS), changed = update.get(30, TimeUnit.SECONDS);
            assertNotEquals(reviewed, changed);
            var result = readiness.findReadinessByCustomerId(customerId).orElseThrow();
            assertEquals(reviewed ? "VERIFIED" : "UNVERIFIED", result.verificationStatus());
            assertEquals(reviewed ? v.verificationId() : null, result.identityVerificationId());
            assertEquals(reviewed ? "Ari Fictional" : "Ari Concurrent", jdbc.queryForObject("select full_name from customer_profiles where customer_id=?", String.class, customerId));
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events where action='CUSTOMER_IDENTITY_VERIFICATION_INVALIDATED' and entity_id=?", Integer.class, customerId));
    }
    @Test void migratesLegacyVerifiedSummaryWithoutFabricatingEvidence() {
        String schema = "identity_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("68").load().migrate();
            UUID legacy = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".customers (id,customer_number,status,verification_status,profile_completion_status) values (?,?,'ACTIVE','VERIFIED','INCOMPLETE')", legacy, "LEGACY-FICTIONAL");
            assertEquals(1, org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("69").load().migrate().migrationsExecuted);
            assertEquals("UNVERIFIED", jdbc.queryForObject("select verification_status from " + schema + ".customers where id=?", String.class, legacy));
            assertEquals(0, jdbc.queryForObject("select count(*) from " + schema + ".customer_identity_verifications", Integer.class));
            assertEquals(0, org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("69").load().migrate().migrationsExecuted);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
    @Test void refusedVerifiedUploadRollsBackVersionAndDeletesCommittedObject() {
        var v = upload(); staff(); service.decide(v.verificationId(), true, verifyRequest(v)); customer();
        var before = storage.findFinalObjectsOlderThan(java.time.Instant.now().plusSeconds(60));
        assertThrows(BusinessStateConflictException.class, () -> service.submitOwn(UUID.randomUUID(), v.evidence().versionId(), pdf("must roll back"), "application/pdf", "rollback.pdf"));
        assertEquals(before, storage.findFinalObjectsOlderThan(java.time.Instant.now().plusSeconds(60)));
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_identity_document_versions dv join customer_identity_documents d on d.id=dv.document_id where d.customer_id=?", Integer.class, customerId));
        String key = jdbc.queryForObject("select storage_key from customer_identity_document_versions where id=?", String.class, v.evidence().versionId());
        assertTrue(documentReferences.existsStorageReference(key));
    }
    @Test void schemaSnapshotIdentityDeltaMatchesExecutableV69Shape() throws Exception {
        String schema = "identity_snapshot_" + UUID.randomUUID().toString().replace("-", "");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("68").load().migrate();
            String snapshot = java.nio.file.Files.readString(java.nio.file.Path.of("../docs/database/MER-DB-CURRENT-SCHEMA.sql"));
            String delta = snapshot.substring(snapshot.indexOf("-- Customer identity evidence, verification history, and immutable Loan provenance."));
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                try { statement.execute("set search_path to " + schema); statement.execute(delta); }
                finally { statement.execute("set search_path to " + SCHEMA); }
            }
            assertEquals(3, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('customer_identity_documents','customer_identity_document_versions','customer_identity_verifications')", Integer.class, schema));
            assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=? and table_name='loan_applications' and column_name='identity_verification_id'", Integer.class, schema));
            String snapshotTrigger = jdbc.queryForObject("select pg_get_functiondef(p.oid) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname=? and p.proname='enforce_loan_identity_provenance'", String.class, schema);
            String migrationTrigger = jdbc.queryForObject("select pg_get_functiondef(p.oid) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname=? and p.proname='enforce_loan_identity_provenance'", String.class, SCHEMA);
            assertEquals(migrationTrigger.replace("\r\n", "\n"), snapshotTrigger.replace(schema + ".", SCHEMA + ".").replace("\r\n", "\n"));
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }

    @Test void allThreeProductsBindOnlySameCustomerVerifiedIdentityAndCannotReplaceIt() {
        var verification = upload();
        for (ProductCode product : ProductCode.values()) {
            assertTrue(assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> insertLoan(product, customerId, verification.verificationId())).getMostSpecificCause()
                    .getMessage().contains("Loan identity verification provenance is invalid"));
        }
        staff(); service.decide(verification.verificationId(), true, verifyRequest(verification));
        UUID otherCustomer = UUID.randomUUID();
        jdbc.update("insert into customers (id,customer_number,status,verification_status,profile_completion_status) values (?,?,'ACTIVE','UNVERIFIED','INCOMPLETE')", otherCustomer, "CUS-" + otherCustomer);
        for (ProductCode product : ProductCode.values()) {
            UUID application = insertLoan(product, customerId, verification.verificationId());
            assertEquals(verification.verificationId(), jdbc.queryForObject("select identity_verification_id from loan_applications where id=?", UUID.class, application));
            assertTrue(assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> insertLoan(product, otherCustomer, verification.verificationId())).getMostSpecificCause()
                    .getMessage().contains("Loan identity verification provenance is invalid"));
            for (UUID replacement : Arrays.asList(UUID.randomUUID(), null)) {
                assertTrue(assertThrows(org.springframework.dao.DataAccessException.class,
                        () -> jdbc.update("update loan_applications set identity_verification_id=? where id=?", replacement, application))
                        .getMostSpecificCause().getMessage().contains("Loan identity verification provenance is immutable"));
            }
            assertEquals(verification.verificationId(), jdbc.queryForObject("select identity_verification_id from loan_applications where id=?", UUID.class, application));
        }
    }

    @Test void historicalNullProvenanceAllowsOrdinaryUpdatesForAllThreeProducts() {
        for (ProductCode product : ProductCode.values()) {
            UUID application = insertLoan(product, customerId, null);
            jdbc.update("update loan_applications set status='CANCELLED',updated_at=now() where id=?", application);
            assertNull(jdbc.queryForObject("select identity_verification_id from loan_applications where id=?", UUID.class, application));
            assertEquals("CANCELLED", jdbc.queryForObject("select status from loan_applications where id=?", String.class, application));
        }
    }

    private UUID insertLoan(ProductCode product, UUID owner, UUID verification) {
        UUID application = UUID.randomUUID();
        jdbc.update("insert into loan_applications (id,customer_id,loan_product_id,application_number,product_code,product_type,status,requested_amount,requested_term_months,submitted_at,identity_verification_id) select ?,?,id,?,product_code,product_type,'CANCELLED',3000000,1,now(),? from loan_products where product_code=?", application, owner, "IDV-" + application, verification, product.name());
        return application;
    }
    @Test void permissionSeedOnlyGrantsReviewerToLoanOfficer() {
        assertEquals(List.of("LOAN_OFFICER"), jdbc.queryForList("select r.code from roles r join role_permissions rp on rp.role_id=r.id join permissions p on p.id=rp.permission_id where p.code='customer:identity:verify'", String.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from role_permissions rp join permissions p on p.id=rp.permission_id join roles r on r.id=rp.role_id where r.code='CUSTOMER' and p.code like 'customer:identity:%:own'", Integer.class));
    }
}
