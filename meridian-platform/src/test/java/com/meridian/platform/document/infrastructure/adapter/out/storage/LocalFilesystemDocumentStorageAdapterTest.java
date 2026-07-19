package com.meridian.platform.document.infrastructure.adapter.out.storage;

import com.meridian.platform.document.application.port.out.StagedDocument;
import com.meridian.platform.document.application.port.out.StoredObject;
import com.meridian.platform.document.domain.model.DocumentVersion;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalFilesystemDocumentStorageAdapterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void stagesValidatesHashesAndAtomicallyCommitsPdf() throws Exception {
        LocalFilesystemDocumentStorageAdapter storage = storage();
        byte[] content = "%PDF-1.7\nfictional evidence".getBytes(StandardCharsets.US_ASCII);

        StagedDocument staged = storage.stage(
                new ByteArrayInputStream(content),
                "application/pdf",
                "recent-payslip.pdf"
        );
        StoredObject stored = storage.commit(staged);

        assertEquals(content.length, staged.byteSize());
        assertEquals(64, staged.sha256Hex().length());
        assertArrayEquals(content, storage.open(stored.storageKey()).readAllBytes());
        assertFalse(stored.storageKey().contains("recent-payslip"));
    }

    @Test
    void rejectsEmptyOversizedMismatchedAndUnsafeFiles() {
        LocalFilesystemDocumentStorageAdapter storage = storage();

        assertInvalid(() -> storage.stage(
                new ByteArrayInputStream(new byte[0]), "application/pdf", "evidence.pdf"
        ));
        assertInvalid(() -> storage.stage(
                new ByteArrayInputStream("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)),
                "image/png",
                "evidence.png"
        ));
        assertInvalid(() -> storage.stage(
                new ByteArrayInputStream(new byte[(int) DocumentVersion.MAX_BYTE_SIZE + 1]),
                "application/pdf",
                "evidence.pdf"
        ));
        assertInvalid(() -> storage.stage(
                new ByteArrayInputStream("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)),
                "application/pdf",
                "../evidence.pdf"
        ));
    }

    @Test
    void findsAndDeletesOnlyOldNonSymlinkOrphans() throws Exception {
        LocalFilesystemDocumentStorageAdapter storage = storage();
        StagedDocument staged = storage.stage(
                new ByteArrayInputStream("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)),
                "application/pdf",
                "evidence.pdf"
        );
        StoredObject stored = storage.commit(staged);
        Path object = tempDirectory.resolve("objects").resolve(stored.storageKey());
        Files.setLastModifiedTime(object, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));

        assertEquals(1, storage.findFinalObjectsOlderThan(Instant.now().minus(1, ChronoUnit.DAYS)).size());
        storage.deleteFinal(stored.storageKey());
        assertFalse(Files.exists(object));
    }

    private LocalFilesystemDocumentStorageAdapter storage() {
        return new LocalFilesystemDocumentStorageAdapter(tempDirectory.toString());
    }

    private void assertInvalid(Runnable action) {
        assertEquals("INVALID_DOCUMENT_UPLOAD", assertThrows(
                BusinessRuleViolationException.class,
                action::run
        ).getErrorCode());
    }
}
