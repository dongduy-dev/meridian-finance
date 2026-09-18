package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.dto.IntakeOcrJobDto;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.service.IntakeOcrService;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
class IntakeOcrCreationPostgreSqlIntegrationTest {

    private static final String SCHEMA = "ocr_create_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired IntakeOcrService service;
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
    void competingCreationRequestsReturnOneJobForTheImmutableVersion() throws Exception {
        UUID staffId = jdbc.queryForObject(
                "SELECT id FROM users WHERE user_type = 'STAFF' ORDER BY id LIMIT 1", UUID.class);
        UUID caseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
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
        jdbc.update("UPDATE intake_documents SET current_version_id = ? WHERE id = ?", versionId, documentId);
        when(currentUsers.currentUser()).thenReturn(new AuthenticatedUser(
                staffId, "loan.officer@meridian.local", "STAFF", null,
                Set.of(), Set.of("document:upload:intake")));
        when(assistedOriginations.authorizeMutation(caseId)).thenReturn(
                new LoanAssistedOriginationPort.AuthorizedIntake(caseId, "UNSECURED_CONSUMER_LOAN"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<IntakeOcrJobDto> command = () -> {
                ready.countDown();
                start.await();
                return service.start(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, versionId);
            };
            Future<IntakeOcrJobDto> first = executor.submit(command);
            Future<IntakeOcrJobDto> second = executor.submit(command);
            ready.await();
            start.countDown();

            assertEquals(first.get().ocrJobId(), second.get().ocrJobId());
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM ocr_jobs WHERE intake_document_version_id = ?",
                Integer.class, versionId));
    }
}
