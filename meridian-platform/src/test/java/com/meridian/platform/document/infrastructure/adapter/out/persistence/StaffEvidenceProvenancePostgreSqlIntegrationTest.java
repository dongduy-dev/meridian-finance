package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.in.QueryStaffDocumentChecklistUseCase;
import com.meridian.platform.document.application.port.in.ReadStaffAssistedActionEvidenceUseCase;
import com.meridian.platform.document.application.port.out.AssistedActionDocumentRepository;
import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.domain.model.*;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionCaseUseCase;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.model.ActorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = { "meridian.loan.offer-expiry.enabled=false", "meridian.document.orphan-reconciliation.enabled=false" })
@Transactional
class StaffEvidenceProvenancePostgreSqlIntegrationTest {
    private static final String SCHEMA = "meridian_provenance_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0, 0, 123456000);
    @Autowired JdbcTemplate jdbc;
    @Autowired AssistedActionDocumentRepository assisted;
    @Autowired DocumentRepository documents;
    @Autowired DocumentChecklistRepository checklists;
    @Autowired DocumentStoragePort storage;
    @Autowired ReadStaffAssistedActionEvidenceUseCase readSigned;
    @Autowired QueryStaffDocumentChecklistUseCase readChecklist;
    @Autowired LoanCorrectionRepository corrections;
    @Autowired LoanApplicationStatusTransitionRepository transitions;
    @Autowired QueryStaffCorrectionCaseUseCase readCorrections;
    @MockitoBean CurrentUserProvider currentUsers;
    private final List<String> storageKeys = new ArrayList<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        registry.add("meridian.document.storage-root", () -> Path.of(System.getProperty("java.io.tmpdir"), SCHEMA).toString());
    }

    @AfterEach
    void removeTestObjects() { storageKeys.forEach(storage::deleteFinal); }

    @Test
    void readsHistoricalBytesForAllThreeFormsAfterCurrentVersionChangesWithoutWritingWorkflowState() throws Exception {
        authorize("document:review");
        UUID app = application();
        for (var type : AssistedActionEvidenceType.values()) {
            var document = new AssistedActionDocument(UUID.randomUUID(), app, type,
                    type == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE ? UUID.randomUUID() : null,
                    type == AssistedActionEvidenceType.CUSTOMER_OFFER_RESPONSE ? AssistedOfferDecision.ACCEPT : null,
                    type == AssistedActionEvidenceType.CUSTOMER_CONTRACT_ACKNOWLEDGMENT ? UUID.randomUUID() : null,
                    type == AssistedActionEvidenceType.CUSTOMER_CONTRACT_ACKNOWLEDGMENT ? 2 : null,
                    type == AssistedActionEvidenceType.CUSTOMER_CANCELLATION_REQUEST ? UUID.randomUUID() : null,
                    null, NOW, NOW);
            assisted.saveDocument(document);
            var old = signedVersion(document.id(), 1, null, type + " old");
            var latest = signedVersion(document.id(), 2, old.id(), type + " latest");
            assisted.saveDocument(document.withCurrentVersion(latest.id(), NOW.plusSeconds(1)));
            try (var content = readSigned.read(app, type, old.id()).content()) {
                assertArrayEquals(pdf(type + " old"), content.readAllBytes());
            }
        }
        long auditsBefore = count("audit_events");
        long versionsBefore = count("assisted_action_document_versions");
        var result = readSigned.query(app);
        assertEquals(3, result.size());
        result.forEach(item -> {
            assertEquals(List.of(1, 2), item.versionHistory().stream().map(version -> version.versionNumber()).toList());
            assertEquals(item.versionHistory().getLast(), item.currentVersion());
        });
        assertEquals(auditsBefore, count("audit_events"));
        assertEquals(versionsBefore, count("assisted_action_document_versions"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM loan_application_status_transitions WHERE loan_application_id = ?", Integer.class, app));
    }

    @Test
    void projectsVersionSpecificReviewReasonsNotesAndBatchedIdentitySummaryForApprover() {
        authorize("approval:decide");
        UUID app = application();
        var checklist = new DocumentChecklist(UUID.randomUUID(), app, DocumentChecklistStage.SUBMISSION, List.of(), NOW);
        checklists.save(checklist);
        var item = new DocumentChecklistItem(UUID.randomUUID(), checklist.id(), DocumentType.RECENT_PAYSLIP, DocumentRequirementStatus.REQUIRED, null, NOW, NOW);
        checklists.saveItem(item);
        var document = new StoredDocument(UUID.randomUUID(), item.id(), null, NOW, NOW);
        documents.saveDocument(document);
        var version = new DocumentVersion(UUID.randomUUID(), document.id(), 1, UUID.randomUUID(), null,
                "payslip.pdf", "application/pdf", "application/pdf", 32, "a".repeat(64), "private-test-" + UUID.randomUUID(), DocumentUploaderActorType.STAFF, STAFF, NOW);
        documents.saveVersion(version);
        documents.saveDocument(document.withCurrentVersion(version.id(), NOW));
        var review = new DocumentReviewDecision(UUID.randomUUID(), item.id(), version.id(), UUID.randomUUID(),
                DocumentReviewOutcome.REQUEST_REPLACEMENT, null, "DOCUMENT_REPLACEMENT_REQUIRED", "Provide all pages", "Internal assessment", STAFF, NOW.plusSeconds(1));
        documents.saveReviewDecision(review);
        checklists.saveItem(item.withCurrentReviewDecision(review.id(), NOW.plusSeconds(1)));
        var projected = readChecklist.query(app).items().getFirst().reviewHistory().getFirst();
        assertEquals(review.id(), projected.reviewDecisionId());
        assertEquals(version.id(), projected.documentVersionId());
        assertEquals("DOCUMENT_REPLACEMENT_REQUIRED", projected.correctionReasonCode());
        assertEquals("Provide all pages", projected.customerInstruction());
        assertTrue(projected.restrictedStaffNoteReadable());
        assertEquals("Internal assessment", projected.restrictedStaffNotes());
        assertEquals(STAFF, projected.reviewer().userId());
        assertFalse(projected.reviewer().displayName().isBlank());
    }

    @Test
    void ordersAllCorrectionRequestsByTimestampAndIdAndMatchesDatabaseNormalizedResubmissionExactly() {
        authorize("loan:correction:staff");
        UUID app = application();
        UUID customer = jdbc.queryForObject("SELECT customer_id FROM loan_applications WHERE id = ?", UUID.class, app);
        UUID customerUser = jdbc.queryForObject("SELECT id FROM users WHERE customer_id = ?", UUID.class, customer);
        UUID first = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID second = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID last = UUID.randomUUID();
        corrections.saveRequest(request(second, app, NOW, NOW.plusSeconds(2)));
        corrections.saveRequest(request(first, app, NOW, NOW.plusSeconds(1)));
        corrections.saveRequest(new LoanCorrectionRequest(last, app, null, "REQUEST_STAFF_CORRECTION", CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED,
                STAFF, LoanCorrectionRequestStatus.OPEN, null, NOW.plusSeconds(3), null, null));
        transitions.save(new LoanApplicationStatusTransition(UUID.randomUUID(), app, UUID.randomUUID(), 1,
                LoanApplicationStatus.RETURNED_FOR_REVISION, LoanApplicationStatus.SUBMITTED, LoanApplicationTransitionAction.RESUBMIT_CORRECTION,
                null, ActorType.USER, customerUser, NOW.plusSeconds(1)));
        var records = corrections.findRequestsByApplicationId(app);
        assertEquals(List.of(first, second, last), records.stream().map(LoanCorrectionRequest::id).toList());
        long before = count("loan_application_status_transitions");
        var result = readCorrections.query(app);
        assertEquals(last, result.correctionRequest().correctionRequestId());
        assertEquals(List.of(first, second, last), result.correctionHistory().stream().map(row -> row.correctionRequestId()).toList());
        assertEquals("CUSTOMER_SELF_SERVICE", result.correctionHistory().getFirst().resubmittedBy().actorType());
        assertNull(result.correctionHistory().getFirst().resubmittedBy().staffActor());
        assertEquals("SUBMITTED", result.correctionHistory().getFirst().resultingApplicationStatus());
        assertEquals("UNAVAILABLE", result.correctionHistory().get(1).resubmittedBy().actorType());
        assertEquals(before, count("loan_application_status_transitions"));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM loan_correction_requests WHERE loan_application_id = ?", Integer.class, app));
    }

    private LoanCorrectionRequest request(UUID id, UUID app, LocalDateTime created, LocalDateTime resubmitted) {
        return new LoanCorrectionRequest(id, app, null, "REQUEST_REPLACEMENT", CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED,
                STAFF, LoanCorrectionRequestStatus.RESUBMITTED, UUID.randomUUID(), created, resubmitted.minusNanos(1000), resubmitted);
    }

    private AssistedActionDocumentVersion signedVersion(UUID documentId, int number, UUID baseline, String text) {
        var staged = storage.stage(new ByteArrayInputStream(pdf(text)), "application/pdf", "signed-" + number + ".pdf");
        var stored = storage.commit(staged);
        storageKeys.add(stored.storageKey());
        return assisted.saveVersion(new AssistedActionDocumentVersion(UUID.randomUUID(), documentId, number, UUID.randomUUID(), baseline,
                staged.originalFilename(), staged.declaredMimeType(), staged.detectedMimeType(), staged.byteSize(), staged.sha256Hex(),
                stored.storageKey(), STAFF, NOW.plusSeconds(number)));
    }

    private static byte[] pdf(String text) { return ("%PDF-1.7\n" + text + "\n%%EOF").getBytes(StandardCharsets.UTF_8); }
    private void authorize(String permission) {
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(STAFF, "staff@meridian.local", "STAFF", null, Set.of("LOAN_OFFICER"), Set.of(permission)));
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private UUID application() {
        UUID app = UUID.randomUUID();
        UUID customer = jdbc.queryForObject("SELECT customer_id FROM users WHERE user_type = 'CUSTOMER' ORDER BY id LIMIT 1", UUID.class);
        UUID product = jdbc.queryForObject("SELECT id FROM loan_products WHERE product_code = 'SALARY_ADVANCE'", UUID.class);
        jdbc.update("INSERT INTO loan_applications (id, customer_id, loan_product_id, application_number, product_code, product_type, status, requested_amount, requested_term_months, submitted_at) "
                + "VALUES (?, ?, ?, ?, 'SALARY_ADVANCE', 'SALARY_BASED', 'SUBMITTED', 3000000, 1, ?)", app, customer, product, "SA-PROVENANCE-" + app, NOW);
        return app;
    }
}
