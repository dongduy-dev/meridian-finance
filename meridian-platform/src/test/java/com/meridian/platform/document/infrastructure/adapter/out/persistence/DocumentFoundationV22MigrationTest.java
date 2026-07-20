package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentFoundationV22MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V22__create_document_checklist_and_review_foundation.sql"
    );

    @Test
    void definesOnlyApprovedDocumentFoundationAndImmutableHistory() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE document_checklists"));
        assertTrue(sql.contains("CREATE TABLE document_checklist_items"));
        assertTrue(sql.contains("CREATE TABLE documents"));
        assertTrue(sql.contains("CREATE TABLE document_versions"));
        assertTrue(sql.contains("CREATE TABLE document_review_decisions"));
        assertTrue(sql.contains("RECENT_PAYSLIP"));
        assertTrue(sql.contains("byte_size > 0 AND byte_size <= 10485760"));
        assertTrue(sql.contains("application/pdf"));
        assertTrue(sql.contains("image/jpeg"));
        assertTrue(sql.contains("image/png"));
        assertTrue(sql.contains("trg_document_versions_immutable"));
        assertTrue(sql.contains("trg_document_review_decisions_immutable"));
        assertTrue(sql.contains("REFERENCES document_versions (id, document_id)"));
        assertTrue(sql.contains("INSERT INTO document_checklists"));
        assertFalse(sql.contains("REJECT_DOCUMENT"));
        assertFalse(sql.contains("document_checklist_templates"));
        assertFalse(sql.contains("ocr"));
    }
}
