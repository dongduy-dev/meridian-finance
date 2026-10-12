package com.meridian.platform.document.infrastructure.adapter.out.storage;

import com.meridian.platform.document.application.port.out.DocumentMalwareScanPort;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.service.DocumentPromotionService;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentPromotionServiceTest {
    @TempDir Path root;
    private final byte[] bytes = "%PDF-1.7\nfictional clean evidence".getBytes();

    @Test
    void scansExactStagedBytesBeforePromotionAndKeepsOpaqueReference() throws Exception {
        DocumentStoragePort storage = spy(new LocalFilesystemDocumentStorageAdapter(root.toString()));
        DocumentMalwareScanPort scanner = mock(DocumentMalwareScanPort.class);
        var staged = storage.stage(new ByteArrayInputStream(bytes), "application/pdf", "evidence.pdf");
        when(scanner.scan(any(), eq(staged.byteSize()))).thenAnswer(call -> {
            assertTrue(storage.findFinalObjectsOlderThan(Instant.MAX).isEmpty());
            assertArrayEquals(bytes, ((java.io.InputStream) call.getArgument(0)).readAllBytes());
            return DocumentMalwareScanPort.ScanResult.CLEAN;
        });
        var stored = new DocumentPromotionService(storage, scanner).promote(staged);
        var order = inOrder(scanner, storage);
        order.verify(scanner).scan(any(), eq(staged.byteSize()));
        order.verify(storage).commit(staged);
        try (var content = storage.open(stored.storageKey())) {
            assertArrayEquals(bytes, content.readAllBytes());
        }
        assertFalse(stored.storageKey().contains("evidence"));
    }

    @Test
    void unsafeUnavailableAndIndeterminateScansNeverPromote() throws Exception {
        for (int outcome = 0; outcome < 3; outcome++) {
            DocumentStoragePort storage = spy(new LocalFilesystemDocumentStorageAdapter(root.toString()));
            DocumentMalwareScanPort scanner = mock(DocumentMalwareScanPort.class);
            var staged = storage.stage(new ByteArrayInputStream(bytes), "application/pdf", "evidence.pdf");
            if (outcome == 0) when(scanner.scan(any(), anyLong())).thenReturn(DocumentMalwareScanPort.ScanResult.UNSAFE);
            if (outcome == 1) when(scanner.scan(any(), anyLong())).thenThrow(new ServiceUnavailableException("DOCUMENT_SCAN_UNAVAILABLE", "Unavailable."));
            if (outcome == 2) when(scanner.scan(any(), anyLong())).thenReturn(null);
            Class<? extends RuntimeException> errorType = outcome == 0
                    ? BusinessRuleViolationException.class : ServiceUnavailableException.class;
            assertThrows(errorType,
                    () -> new DocumentPromotionService(storage, scanner).promote(staged));
            verify(storage, never()).commit(any());
            assertTrue(storage.findFinalObjectsOlderThan(Instant.MAX).isEmpty());
            // Existing upload owners retain cleanup in finally, including scanner failure.
            storage.discardStaged(staged);
            try (var files = Files.list(root.resolve("staging"))) { assertEquals(0, files.count()); }
        }
    }

    @Test
    void changedOrPartiallyScannedStagingCannotPromote() throws Exception {
        DocumentStoragePort storage = spy(new LocalFilesystemDocumentStorageAdapter(root.toString()));
        var staged = storage.stage(new ByteArrayInputStream(bytes), "application/pdf", "evidence.pdf");
        DocumentMalwareScanPort scanner = (content, size) -> DocumentMalwareScanPort.ScanResult.CLEAN;
        assertThrows(ServiceUnavailableException.class, () -> new DocumentPromotionService(storage, scanner).promote(staged));
        verify(storage, never()).commit(any());
        storage.discardStaged(staged);
    }

    @Test
    void streamCloseFailureCannotPromote() throws Exception {
        DocumentStoragePort storage = mock(DocumentStoragePort.class);
        var actual = new LocalFilesystemDocumentStorageAdapter(root.toString());
        var staged = actual.stage(new ByteArrayInputStream(bytes), "application/pdf", "evidence.pdf");
        when(storage.openStaged(staged)).thenReturn(new ByteArrayInputStream(bytes) {
            @Override public void close() throws IOException { throw new IOException("private storage detail"); }
        });
        DocumentMalwareScanPort scanner = (content, size) -> {
            try { content.readAllBytes(); } catch (IOException e) { throw new AssertionError(e); }
            return DocumentMalwareScanPort.ScanResult.CLEAN;
        };
        assertThrows(ServiceUnavailableException.class, () -> new DocumentPromotionService(storage, scanner).promote(staged));
        verify(storage, never()).commit(any());
    }
}
