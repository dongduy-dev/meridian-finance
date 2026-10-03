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
    @Test void fullNameInvalidatesButContactAndEmploymentDoNot() {
        var v = upload(); staff(); service.decide(v.verificationId(), true, verifyRequest(v)); customer();
        profiles.updateOwnProfile(profile("Ari Fictional", null, "0911111111"));
        assertEquals("VERIFIED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        profiles.updateOwnProfile(profile("Ari Changed", null, "0911111111"));
        assertEquals("UNVERIFIED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        assertNull(readiness.findReadinessByCustomerId(customerId).orElseThrow().identityVerificationId());
        assertEquals("VERIFIED", service.ownHistory().getFirst().status());
        assertThrows(BusinessStateConflictException.class, () -> profiles.updateOwnProfile(profile("Ari Changed", "DIFFERENT-REFERENCE", "0911111111")));
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
    @Test void staffNameChangeInvalidatesWhileOtherMutableFactsPreserveVerification() {
        var v = upload(); staff(); service.decide(v.verificationId(), true, verifyRequest(v));
        auth("STAFF", null, OFFICER, Set.of("customer:intake:manage"));
        staffProfiles.updateProfile(customerId, new UpdateCustomerProfileRequest("Ari Fictional", null,
                "0922222222", "Changed fictional address", "SELF_EMPLOYED", null, true, true));
        assertEquals("VERIFIED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        staffProfiles.updateProfile(customerId, profile("Ari Staff Changed", null, "0922222222"));
        assertEquals("UNVERIFIED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action = 'CUSTOMER_IDENTITY_VERIFICATION_INVALIDATED' and entity_id = ?", Integer.class, customerId));
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
            var review = executor.submit(() -> { staff(); latch.await(); try { service.decide(v.verificationId(), true, verifyRequest(v)); }
                catch (BusinessStateConflictException expected) { assertEquals("IDENTITY_VERIFICATION_EVIDENCE_STALE", expected.getErrorCode()); }
                finally { SecurityContextHolder.clearContext(); } return true; });
            var update = executor.submit(() -> { customer(); latch.await(); try { profiles.updateOwnProfile(profile("Ari Concurrent", null, "0911111111")); }
                finally { SecurityContextHolder.clearContext(); } return true; });
            latch.countDown(); review.get(30, TimeUnit.SECONDS); update.get(30, TimeUnit.SECONDS);
        }
        assertEquals("UNVERIFIED", readiness.findReadinessByCustomerId(customerId).orElseThrow().verificationStatus());
        assertNull(readiness.findReadinessByCustomerId(customerId).orElseThrow().identityVerificationId());
    }
    @Test void migratesLegacyVerifiedSummaryWithoutFabricatingEvidence() {
        String schema = "identity_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("68").load().migrate();
            UUID legacy = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".customers (id,customer_number,status,verification_status,profile_completion_status) values (?,?,'ACTIVE','VERIFIED','INCOMPLETE')", legacy, "LEGACY-FICTIONAL");
            assertEquals(1, org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load().migrate().migrationsExecuted);
            assertEquals("UNVERIFIED", jdbc.queryForObject("select verification_status from " + schema + ".customers where id=?", String.class, legacy));
            assertEquals(0, jdbc.queryForObject("select count(*) from " + schema + ".customer_identity_verifications", Integer.class));
            assertEquals(0, org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load().migrate().migrationsExecuted);
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
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
    @Test void permissionSeedOnlyGrantsReviewerToLoanOfficer() {
        assertEquals(List.of("LOAN_OFFICER"), jdbc.queryForList("select r.code from roles r join role_permissions rp on rp.role_id=r.id join permissions p on p.id=rp.permission_id where p.code='customer:identity:verify'", String.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from role_permissions rp join permissions p on p.id=rp.permission_id join roles r on r.id=rp.role_id where r.code='CUSTOMER' and p.code like 'customer:identity:%:own'", Integer.class));
    }
}
