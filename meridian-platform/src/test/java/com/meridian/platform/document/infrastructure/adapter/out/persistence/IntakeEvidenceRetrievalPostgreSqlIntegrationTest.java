package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.dto.UploadIntakeEvidenceCommand;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.service.IntakeEvidenceService;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false", "meridian.document.orphan-reconciliation.enabled=false"})
class IntakeEvidenceRetrievalPostgreSqlIntegrationTest {
    private static final String SCHEMA = "intake_read_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired IntakeEvidenceService service;
    @Autowired IntakeDocumentRepository documents;
    @Autowired DocumentStoragePort storage;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CurrentUserProvider users;
    @MockitoBean BusinessAuditPublisher audits;
    private final UUID caseId = UUID.randomUUID();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @AfterEach
    void cleanUpStorage() {
        documents.findByCase(caseId).forEach(document -> documents.findVersionsByDocumentId(document.id())
                .forEach(version -> storage.deleteFinal(version.storageKey())));
    }

    @Test
    void storedOrderedHistoryRetrievesExactBytesAfterIntakeEndsWithoutChangingEvidenceOrWorkflow() throws Exception {
        UUID staffId = jdbc.queryForObject("SELECT id FROM users WHERE user_type = 'STAFF' ORDER BY id LIMIT 1", UUID.class);
        when(users.currentUser()).thenReturn(new AuthenticatedUser(staffId, "staff@meridian.local", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("loan:originate:staff", "document:upload:intake")));
        jdbc.update("INSERT INTO assisted_origination_cases (id, product_code, status, created_by_staff_user_id, created_at, updated_at) "
                + "VALUES (?, 'UNSECURED_CONSUMER_LOAN', 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", caseId, staffId);
        byte[] firstBytes = "%PDF-1.4\nfictional original paper\n%%EOF".getBytes(StandardCharsets.UTF_8);
        byte[] secondBytes = "%PDF-1.4\nfictional replacement paper\n%%EOF".getBytes(StandardCharsets.UTF_8);
        var first = service.upload(command("original paper.pdf", firstBytes, null));
        var second = service.upload(command("replacement paper.pdf", secondBytes, first.intakeDocumentVersionId()));
        jdbc.update("UPDATE assisted_origination_cases SET status = 'ABANDONED', terminal_at = CURRENT_TIMESTAMP WHERE id = ?", caseId);
        var caseBefore = jdbc.queryForMap("SELECT * FROM assisted_origination_cases WHERE id = ?", caseId);
        var documentBefore = jdbc.queryForMap("SELECT * FROM intake_documents WHERE assisted_origination_case_id = ?", caseId);
        clearInvocations(audits);

        var evidence = service.findEvidence(caseId).getFirst();
        assertEquals(second.intakeDocumentVersionId(), evidence.currentVersionId());
        assertEquals(java.util.List.of(first.intakeDocumentVersionId(), second.intakeDocumentVersionId()),
                evidence.versions().stream().map(version -> version.intakeDocumentVersionId()).toList());
        assertEquals(java.util.List.of(1, 2), evidence.versions().stream().map(version -> version.versionNumber()).toList());
        assertEquals("original paper.pdf", evidence.versions().getFirst().originalFilename());
        assertEquals(firstBytes.length, evidence.versions().getFirst().byteSize());
        try (var stream = service.readContent(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION,
                first.intakeDocumentVersionId()).content()) { assertArrayEquals(firstBytes, stream.readAllBytes()); }
        var current = service.readContent(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, second.intakeDocumentVersionId());
        assertEquals("replacement paper.pdf", current.originalFilename());
        assertEquals("application/pdf", current.detectedMimeType());
        assertEquals(secondBytes.length, current.byteSize());
        try (var stream = current.content()) { assertArrayEquals(secondBytes, stream.readAllBytes()); }
        assertEquals(caseBefore, jdbc.queryForMap("SELECT * FROM assisted_origination_cases WHERE id = ?", caseId));
        assertEquals(documentBefore, jdbc.queryForMap("SELECT * FROM intake_documents WHERE assisted_origination_case_id = ?", caseId));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM intake_document_versions", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ocr_jobs", Integer.class));
        verifyNoInteractions(audits);
    }

    private UploadIntakeEvidenceCommand command(String filename, byte[] bytes, UUID baseline) {
        return new UploadIntakeEvidenceCommand(caseId, IntakeEvidenceType.UCL_PAPER_APPLICATION, UUID.randomUUID(),
                baseline, filename, "application/pdf", new ByteArrayInputStream(bytes));
    }
}
