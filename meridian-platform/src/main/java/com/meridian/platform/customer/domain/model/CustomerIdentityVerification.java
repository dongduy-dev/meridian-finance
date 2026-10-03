package com.meridian.platform.customer.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record CustomerIdentityVerification(
        UUID id, UUID customerId, int sequence, Source source, UUID caseId,
        UUID documentVersionId, String identityFullName, Status status, RejectionReason rejectionReason,
        UUID submittedBy, LocalDateTime submittedAt, UUID reviewedBy, LocalDateTime completedAt,
        UUID decisionRequestId) {
    public enum Source { CUSTOMER_DIGITAL, STAFF_ASSISTED_INTAKE }
    public enum Status { PENDING_REVIEW, VERIFIED, REJECTED, SUPERSEDED }
    public enum RejectionReason { IDENTITY_REFERENCE_MISMATCH, NAME_MISMATCH, UNREADABLE_EVIDENCE, UNACCEPTABLE_EVIDENCE }
    public CustomerIdentityVerification {
        Objects.requireNonNull(id); Objects.requireNonNull(customerId); Objects.requireNonNull(source);
        Objects.requireNonNull(documentVersionId); Objects.requireNonNull(identityFullName);
        Objects.requireNonNull(status); Objects.requireNonNull(submittedBy); Objects.requireNonNull(submittedAt);
        if (sequence < 1 || (source == Source.STAFF_ASSISTED_INTAKE) != (caseId != null))
            throw new IllegalArgumentException("Invalid verification evidence binding.");
        boolean decision = status == Status.VERIFIED || status == Status.REJECTED;
        if (decision != (reviewedBy != null && decisionRequestId != null)
                || (status != Status.PENDING_REVIEW) != (completedAt != null)
                || (status == Status.REJECTED) != (rejectionReason != null))
            throw new IllegalArgumentException("Invalid verification outcome.");
    }
    public CustomerIdentityVerification complete(Status outcome, RejectionReason reason, UUID actor, UUID requestId, LocalDateTime now) {
        if (status != Status.PENDING_REVIEW)
            throw new BusinessStateConflictException("IDENTITY_VERIFICATION_ALREADY_COMPLETED", "Identity verification has already completed.");
        if (outcome != Status.VERIFIED && outcome != Status.REJECTED)
            throw new IllegalArgumentException("A manual verification decision is required.");
        return new CustomerIdentityVerification(id, customerId, sequence, source, caseId, documentVersionId,
                identityFullName, outcome, reason, submittedBy, submittedAt, actor, now, requestId);
    }
    public CustomerIdentityVerification supersede(LocalDateTime now) {
        if (status != Status.PENDING_REVIEW) throw new IllegalStateException("Only pending evidence can be superseded.");
        return new CustomerIdentityVerification(id, customerId, sequence, source, caseId, documentVersionId,
                identityFullName, Status.SUPERSEDED, null, submittedBy, submittedAt, null, now, null);
    }
    @Override public String toString() { return "CustomerIdentityVerification[id=" + id + ", status=" + status + "]"; }
}
