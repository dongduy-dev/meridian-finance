package com.meridian.platform.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

/** Persisted, fictional readiness fixture for lending regression tests.
 * The verification workflow itself is exercised through real commands in Customer's PostgreSQL tests.
 */
public final class CustomerIdentityVerificationFixture {
    private CustomerIdentityVerificationFixture() { }

    public static UUID verified(JdbcTemplate jdbc, UUID customerId) {
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000302");
        String fullName = jdbc.queryForObject("select full_name from customer_profiles where customer_id=?", String.class, customerId);
        UUID submitter = jdbc.queryForObject("select id from users where customer_id=?", UUID.class, customerId);
        jdbc.update("insert into customer_identity_documents (id,customer_id,created_at,updated_at) values (?,?,'2026-01-01','2026-01-01')", documentId, customerId);
        jdbc.update("insert into customer_identity_document_versions (id,document_id,version_number,upload_request_id,original_filename,declared_mime_type,detected_mime_type,byte_size,sha256_hex,storage_key,uploader_user_id,uploaded_at) values (?,?,1,?,'fictional-fixture.pdf','application/pdf','application/pdf',16,?,?,?,'2026-01-01')", versionId, documentId, UUID.randomUUID(), "a".repeat(64), "fictional-identity-fixture/" + versionId, submitter);
        jdbc.update("update customer_identity_documents set current_version_id=? where id=?", versionId, documentId);
        jdbc.update("insert into customer_identity_verifications (id,customer_id,sequence_number,evidence_source,document_version_id,identity_full_name,status,submitted_by,submitted_at) values (?,?,1,'CUSTOMER_DIGITAL',?,?,'PENDING_REVIEW',?,'2026-01-01')", verificationId, customerId, versionId, fullName, submitter);
        jdbc.update("update customer_identity_verifications set status='VERIFIED',reviewed_by=?,completed_at='2026-01-01',decision_request_id=? where id=?", actor, UUID.randomUUID(), verificationId);
        jdbc.update("update customers set verification_status='VERIFIED' where id=?", customerId);
        return verificationId;
    }
}
