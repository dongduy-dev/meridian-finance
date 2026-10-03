package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.application.port.out.CustomerIdentityVerificationRepository;
import com.meridian.platform.customer.domain.model.CustomerIdentityVerification;
import com.meridian.platform.customer.domain.model.CustomerIdentityVerification.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerIdentityVerificationRepositoryAdapter implements CustomerIdentityVerificationRepository {
    private final JdbcTemplate jdbc;
    public CustomerIdentityVerificationRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lockDecisionRequest(UUID requestId) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, "customer-identity-decision:" + requestId);
    }
    public Optional<CustomerIdentityVerification> findByDecisionRequest(UUID requestId) {
        return jdbc.query("select * from customer_identity_verifications where decision_request_id = ?", MAPPER, requestId).stream().findFirst();
    }
    private static final RowMapper<CustomerIdentityVerification> MAPPER = (r, n) -> new CustomerIdentityVerification(
            r.getObject("id", UUID.class), r.getObject("customer_id", UUID.class), r.getInt("sequence_number"),
            Source.valueOf(r.getString("evidence_source")), r.getObject("assisted_origination_case_id", UUID.class),
            r.getObject("document_version_id", UUID.class), r.getString("identity_full_name"), Status.valueOf(r.getString("status")),
            r.getString("rejection_reason") == null ? null : RejectionReason.valueOf(r.getString("rejection_reason")),
            r.getObject("submitted_by", UUID.class), r.getTimestamp("submitted_at").toLocalDateTime(),
            r.getObject("reviewed_by", UUID.class), r.getTimestamp("completed_at") == null ? null : r.getTimestamp("completed_at").toLocalDateTime(),
            r.getObject("decision_request_id", UUID.class));
    public Optional<CustomerIdentityVerification> findById(UUID id) {
        return jdbc.query("select * from customer_identity_verifications where id = ?", MAPPER, id).stream().findFirst();
    }
    public List<CustomerIdentityVerification> findByCustomer(UUID customerId) {
        return jdbc.query("select * from customer_identity_verifications where customer_id = ? order by sequence_number desc", MAPPER, customerId);
    }
    public List<CustomerIdentityVerification> findPending(int offset, int limit) {
        return jdbc.query("select * from customer_identity_verifications where status = 'PENDING_REVIEW' order by submitted_at, id limit ? offset ?", MAPPER, limit, offset);
    }
    public CustomerIdentityVerification save(CustomerIdentityVerification v) {
        if (findById(v.id()).isPresent()) {
            jdbc.update("update customer_identity_verifications set status = ?, rejection_reason = ?, reviewed_by = ?, completed_at = ?, decision_request_id = ? where id = ?",
                    v.status().name(), v.rejectionReason() == null ? null : v.rejectionReason().name(), v.reviewedBy(), v.completedAt(), v.decisionRequestId(), v.id());
            return v;
        }
        jdbc.update("""
                insert into customer_identity_verifications (id, customer_id, sequence_number, evidence_source,
                  assisted_origination_case_id, document_version_id, identity_full_name, status, rejection_reason,
                  submitted_by, submitted_at, reviewed_by, completed_at, decision_request_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, v.id(), v.customerId(), v.sequence(), v.source().name(), v.caseId(), v.documentVersionId(),
                v.identityFullName(), v.status().name(), v.rejectionReason() == null ? null : v.rejectionReason().name(),
                v.submittedBy(), v.submittedAt(), v.reviewedBy(), v.completedAt(), v.decisionRequestId());
        return v;
    }
}
