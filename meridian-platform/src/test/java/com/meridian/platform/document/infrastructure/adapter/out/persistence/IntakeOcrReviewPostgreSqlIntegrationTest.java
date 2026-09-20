package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.dto.FinalizeIntakeOcrReviewRequest;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.port.out.OcrResultCipher;
import com.meridian.platform.document.application.service.IntakeOcrReviewService;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
class IntakeOcrReviewPostgreSqlIntegrationTest {

    private static final String SCHEMA = "ocr_review_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired IntakeOcrReviewService service;
    @Autowired OcrResultCipher cipher;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CurrentUserProvider currentUsers;
    @MockitoBean LoanAssistedOriginationPort assistedOriginations;
    @MockitoBean BusinessAuditPublisher auditPublisher;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @Test
    void competingDifferentReviewsCreateOneTerminalRecord() throws Exception {
        UUID staffId = jdbc.queryForObject(
                "SELECT id FROM users WHERE user_type = 'STAFF' ORDER BY id LIMIT 1", UUID.class);
        UUID caseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        jdbc.update("INSERT INTO assisted_origination_cases "
                        + "(id, product_code, status, created_by_staff_user_id, created_at, updated_at) "
                        + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                caseId, staffId);
        jdbc.update("INSERT INTO intake_documents "
                        + "(id, assisted_origination_case_id, evidence_type, created_at, updated_at) "
                        + "VALUES (?, ?, 'UCL_PAPER_APPLICATION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                documentId, caseId);
        jdbc.update("INSERT INTO intake_document_versions "
                        + "(id, intake_document_id, version_number, upload_request_id, original_filename, "
                        + "declared_mime_type, detected_mime_type, byte_size, sha256_hex, storage_key, "
                        + "uploader_staff_user_id, uploaded_at, created_at) "
                        + "VALUES (?, ?, 1, ?, 'ucl.pdf', 'application/pdf', 'application/pdf', "
                        + "128, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                versionId, documentId, UUID.randomUUID(), "a".repeat(64),
                "ab/" + UUID.randomUUID(), staffId);
        jdbc.update("UPDATE intake_documents SET current_version_id = ? WHERE id = ?",
                versionId, documentId);
        jdbc.update("INSERT INTO ocr_jobs "
                        + "(id, intake_document_version_id, evidence_type, source_storage_key, "
                        + "source_mime_type, source_sha256_hex, state, attempt_count, next_attempt_at, "
                        + "trace_id, created_at, updated_at, completed_at) "
                        + "VALUES (?, ?, 'UCL_PAPER_APPLICATION', 'ab/opaque', 'application/pdf', ?, "
                        + "'COMPLETED', 1, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                jobId, versionId, "a".repeat(64), UUID.randomUUID());
        jdbc.update("INSERT INTO ocr_results "
                        + "(id, ocr_job_id, provider, processor_name, encrypted_extracted_text, "
                        + "encrypted_normalized_payload, encrypted_structured_suggestions, disposition, "
                        + "processing_duration_ms, created_at) VALUES (?, ?, 'TEST', 'test', ?, ?, ?, "
                        + "'PENDING_REVIEW', 1, CURRENT_TIMESTAMP)",
                resultId, jobId, cipher.encrypt("raw"), cipher.encrypt("{}"), cipher.encrypt("[]"));
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(
                staffId, "staff@meridian.local", "STAFF", null,
                Set.of(), Set.of("document:upload:intake")));
        when(assistedOriginations.authorizeMutation(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> review(
                    ready, start, caseId, versionId, resultId, "First"));
            Future<Boolean> second = executor.submit(() -> review(
                    ready, start, caseId, versionId, resultId, "Second"));
            ready.await();
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM ocr_reviews WHERE ocr_result_id = ?", Integer.class, resultId));
        assertEquals("REVIEWED", jdbc.queryForObject(
                "SELECT disposition FROM ocr_results WHERE id = ?", String.class, resultId));
    }

    private boolean review(
            CountDownLatch ready,
            CountDownLatch start,
            UUID caseId,
            UUID versionId,
            UUID resultId,
            String value
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.finalizeReview(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId,
                    new FinalizeIntakeOcrReviewRequest(resultId, Map.of("fullName", value)));
            return true;
        } catch (BusinessStateConflictException exception) {
            return false;
        }
    }
}
