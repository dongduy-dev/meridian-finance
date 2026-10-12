package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.in.CustomerIdentityEvidenceUseCase;
import com.meridian.platform.document.application.port.out.DocumentMalwareScanPort;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"meridian.loan.offer-expiry.enabled=false", "meridian.document.orphan-reconciliation.enabled=false"})
class DocumentScanPostgreSqlIntegrationTest {
    private static final String SCHEMA = "meridian_scan_" + UUID.randomUUID().toString().replace("-", "");
    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), SCHEMA);
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 8, 0);
    @Autowired CustomerIdentityEvidenceUseCase evidence;
    @Autowired JdbcTemplate jdbc;
    // This context alone controls failure outcomes; the normal suite uses real ClamAV.
    @MockitoBean DocumentMalwareScanPort scanner;
    private UUID customer;
    private long existingObjects;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        registry.add("meridian.document.storage-root", () -> ROOT.toString());
    }

    @BeforeEach void customer() throws Exception {
        customer = UUID.randomUUID();
        try (var files = Files.walk(ROOT.resolve("objects"))) {
            existingObjects = files.filter(Files::isRegularFile).count();
        }
        jdbc.update("insert into customers (id, customer_number, status, verification_status, profile_completion_status, created_at, updated_at) values (?, ?, 'ACTIVE', 'UNVERIFIED', 'INCOMPLETE', now(), now())", customer, "CUS-" + customer);
    }

    @Test void unsafeAndUnavailableScanRollBackNewAggregateAndLeaveNoUsableVersionOrObject() throws Exception {
        when(scanner.scan(any(), anyLong())).thenReturn(DocumentMalwareScanPort.ScanResult.UNSAFE);
        assertThrows(BusinessRuleViolationException.class, () -> store(UUID.randomUUID(), null, "new"));
        assertNoDocument();
        doThrow(new ServiceUnavailableException("DOCUMENT_SCAN_UNAVAILABLE", "Unavailable.")).when(scanner).scan(any(), anyLong());
        assertThrows(ServiceUnavailableException.class, () -> store(UUID.randomUUID(), null, "new"));
        assertNoDocument();
        try (var files = Files.list(ROOT.resolve("staging"))) { assertEquals(0, files.count()); }
    }

    @Test void exactReplayWhileScannerOfflineKeepsVersionAndReplacementFailsWithoutAdvancing() throws Exception {
        when(scanner.scan(any(), anyLong())).thenAnswer(call -> {
            ((InputStream) call.getArgument(0)).readAllBytes();
            return DocumentMalwareScanPort.ScanResult.CLEAN;
        });
        UUID request = UUID.randomUUID();
        var first = store(request, null, "original");
        clearInvocations(scanner);
        doThrow(new ServiceUnavailableException("DOCUMENT_SCAN_UNAVAILABLE", "Unavailable.")).when(scanner).scan(any(), anyLong());
        assertEquals(first, store(request, null, "original"));
        verifyNoInteractions(scanner);
        assertThrows(BusinessStateConflictException.class, () -> store(request, null, "different"));
        verifyNoInteractions(scanner);
        assertThrows(ServiceUnavailableException.class, () -> store(UUID.randomUUID(), first.versionId(), "replacement"));
        verify(scanner).scan(any(), anyLong());
        assertEquals(first.versionId(), jdbc.queryForObject("select current_version_id from customer_identity_documents where customer_id=?", UUID.class, customer));
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_identity_document_versions v join customer_identity_documents d on d.id=v.document_id where d.customer_id=?", Integer.class, customer));
        try (var files = Files.list(ROOT.resolve("staging"))) { assertEquals(0, files.count()); }
    }

    private CustomerIdentityEvidenceUseCase.Evidence store(UUID request, UUID baseline, String label) {
        return evidence.store(customer, request, baseline, new ByteArrayInputStream(("%PDF-1.7\nfictional " + label).getBytes()),
                "application/pdf", "identity.pdf", ACTOR, NOW);
    }

    private void assertNoDocument() throws Exception {
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_identity_documents where customer_id=?", Integer.class, customer));
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_identity_document_versions v join customer_identity_documents d on d.id=v.document_id where d.customer_id=?", Integer.class, customer));
        try (var files = Files.walk(ROOT.resolve("objects"))) { assertEquals(existingObjects, files.filter(Files::isRegularFile).count()); }
    }
}
