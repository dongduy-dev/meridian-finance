package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.CustomerIdentityDocumentRepository;
import com.meridian.platform.document.domain.model.CustomerIdentityDocumentVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerIdentityDocumentRepositoryAdapter implements CustomerIdentityDocumentRepository {
    private final JdbcTemplate jdbc;
    public CustomerIdentityDocumentRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<CustomerIdentityDocumentVersion> MAPPER = (r, n) -> new CustomerIdentityDocumentVersion(
            r.getObject("id", UUID.class), r.getObject("document_id", UUID.class), r.getInt("version_number"),
            r.getObject("upload_request_id", UUID.class), r.getObject("baseline_version_id", UUID.class),
            r.getString("original_filename"), r.getString("declared_mime_type"), r.getString("detected_mime_type"),
            r.getLong("byte_size"), r.getString("sha256_hex"), r.getString("storage_key"),
            r.getObject("uploader_user_id", UUID.class), r.getTimestamp("uploaded_at").toLocalDateTime());
    public void lockUploadRequest(UUID requestId) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, "customer-identity-upload:" + requestId);
    }
    public Document lockOrCreate(UUID customerId, LocalDateTime now) {
        jdbc.update("insert into customer_identity_documents (id, customer_id, created_at, updated_at) values (?, ?, ?, ?) on conflict (customer_id) do nothing", UUID.randomUUID(), customerId, now, now);
        return find(customerId, true).orElseThrow();
    }
    public Optional<Document> find(UUID customerId, boolean lock) {
        return jdbc.query("select * from customer_identity_documents where customer_id = ?" + (lock ? " for update" : ""),
                (r, n) -> new Document(r.getObject("id", UUID.class), r.getObject("customer_id", UUID.class), r.getObject("current_version_id", UUID.class)), customerId).stream().findFirst();
    }
    public Optional<CustomerIdentityDocumentVersion> findVersion(UUID id) {
        return jdbc.query("select * from customer_identity_document_versions where id = ?", MAPPER, id).stream().findFirst();
    }
    public Optional<CustomerIdentityDocumentVersion> findUpload(UUID requestId) {
        return jdbc.query("select * from customer_identity_document_versions where upload_request_id = ?", MAPPER, requestId).stream().findFirst();
    }
    public void saveVersion(CustomerIdentityDocumentVersion v) {
        jdbc.update("""
                insert into customer_identity_document_versions (id, document_id, version_number, upload_request_id,
                  baseline_version_id, original_filename, declared_mime_type, detected_mime_type, byte_size,
                  sha256_hex, storage_key, uploader_user_id, uploaded_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, v.id(), v.documentId(), v.versionNumber(), v.uploadRequestId(), v.baselineVersionId(),
                v.originalFilename(), v.declaredMimeType(), v.detectedMimeType(), v.byteSize(), v.sha256Hex(), v.storageKey(), v.uploaderUserId(), v.uploadedAt());
    }
    public void advance(UUID id, UUID versionId, LocalDateTime now) {
        jdbc.update("update customer_identity_documents set current_version_id = ?, updated_at = ? where id = ?", versionId, now, id);
    }
}
