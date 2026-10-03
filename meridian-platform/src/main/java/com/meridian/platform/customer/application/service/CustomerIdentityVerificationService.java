package com.meridian.platform.customer.application.service;

import com.meridian.platform.customer.application.dto.*;
import com.meridian.platform.customer.application.port.in.CustomerIdentityVerificationUseCase;
import com.meridian.platform.customer.application.port.out.*;
import com.meridian.platform.customer.domain.model.*;
import com.meridian.platform.customer.domain.model.CustomerIdentityVerification.*;
import com.meridian.platform.shared.application.audit.*;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.audit.*;
import com.meridian.platform.shared.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CustomerIdentityVerificationService implements CustomerIdentityVerificationUseCase {
    private final CustomerRepository customers;
    private final CustomerIdentityVerificationRepository verifications;
    private final CustomerIdentityEvidencePort evidence;
    private final CustomerSensitiveValueProtector protector;
    private final CurrentUserProvider users;
    private final BusinessAuditPublisher audits;
    private final Clock clock;
    public CustomerIdentityVerificationService(CustomerRepository customers, CustomerIdentityVerificationRepository verifications,
            CustomerIdentityEvidencePort evidence, CustomerSensitiveValueProtector protector, CurrentUserProvider users,
            BusinessAuditPublisher audits, Clock clock) {
        this.customers = customers; this.verifications = verifications; this.evidence = evidence;
        this.protector = protector; this.users = users; this.audits = audits; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<CustomerIdentityVerificationDto> ownHistory() {
        UUID customerId = own("customer:identity:read:own").requireCustomerId();
        Customer customer = customer(customerId, false);
        return verifications.findByCustomer(customerId).stream().map(v -> dto(v, customer, true)).toList();
    }
    @Transactional
    public CustomerIdentityVerificationDto submitOwn(UUID requestId, UUID baseline, InputStream content, String mime, String filename) {
        var actor = own("customer:identity:write:own");
        Customer customer = customer(actor.requireCustomerId(), true);
        requireProfile(customer);
        LocalDateTime now = now();
        var stored = evidence.store(customer.id(), requestId, baseline, content, mime, filename, actor.userId(), now);
        var history = verifications.findByCustomer(customer.id());
        var replay = history.stream().filter(v -> v.documentVersionId().equals(stored.versionId())).findFirst().orElse(null);
        if (replay != null) return dto(replay, customer, true);
        if (customer.verificationStatus() == VerificationStatus.VERIFIED) throw notAllowed();
        return begin(customer, Source.CUSTOMER_DIGITAL, null, stored.versionId(), actor, history, now);
    }
    @Transactional
    public CustomerIdentityVerificationDto submitIntake(UUID caseId, UUID versionId) {
        var actor = staff();
        UUID customerId = evidence.lockIntakeCustomer(caseId, true);
        Customer customer = customer(customerId, true);
        requireProfile(customer);
        evidence.requireCurrent(customerId, caseId, versionId);
        var history = verifications.findByCustomer(customerId);
        var replay = history.stream().filter(v -> Objects.equals(v.caseId(), caseId) && v.documentVersionId().equals(versionId)).findFirst().orElse(null);
        if (replay != null) return dto(replay, customer, true);
        if (customer.verificationStatus() == VerificationStatus.VERIFIED) throw notAllowed();
        return begin(customer, Source.STAFF_ASSISTED_INTAKE, caseId, versionId, actor, history, now());
    }
    private CustomerIdentityVerificationDto begin(Customer customer, Source source, UUID caseId, UUID versionId,
            AuthenticatedUser actor, List<CustomerIdentityVerification> history, LocalDateTime now) {
        history.stream().filter(v -> v.status() == Status.PENDING_REVIEW).forEach(v -> verifications.save(v.supersede(now)));
        var v = new CustomerIdentityVerification(UUID.randomUUID(), customer.id(), history.isEmpty() ? 1 : history.getFirst().sequence() + 1,
                source, caseId, versionId, customer.profile().fullName(), Status.PENDING_REVIEW, null, actor.userId(), now, null, null, null);
        verifications.save(v);
        Customer saved = customers.save(customer.withVerificationStatus(VerificationStatus.UNVERIFIED, now));
        audit(v, actor, BusinessAuditAction.CUSTOMER_IDENTITY_EVIDENCE_SUBMITTED, now);
        return dto(v, saved, true);
    }
    @Transactional(readOnly = true)
    public List<CustomerIdentityVerificationDto> pending(int page, int size) {
        staff();
        if (page < 0 || page > 10000 || size < 1 || size > 100) throw new BusinessRuleViolationException("INVALID_IDENTITY_VERIFICATION_REQUEST", "Invalid verification page.");
        return verifications.findPending(page * size, size).stream().map(v -> dto(v, customer(v.customerId(), false), false)).toList();
    }
    @Transactional(readOnly = true)
    public CustomerIdentityVerificationDto detail(UUID id) {
        staff(); var v = find(id);
        return dto(v, customer(v.customerId(), false), true);
    }
    @Transactional
    public CustomerIdentityVerificationDto decide(UUID id, boolean verify, CustomerIdentityDecisionRequest request) {
        var actor = staff(); Objects.requireNonNull(request); Objects.requireNonNull(request.requestId());
        verifications.lockDecisionRequest(request.requestId());
        if (verifications.findByDecisionRequest(request.requestId()).filter(v -> !v.id().equals(id)).isPresent())
            throw new BusinessStateConflictException("IDEMPOTENCY_KEY_REUSED", "The request ID was already used for a different identity decision.");
        var found = find(id);
        if (found.caseId() != null && !found.customerId().equals(evidence.lockIntakeCustomer(found.caseId(), false))) throw stale();
        Customer customer = customer(found.customerId(), true);
        var v = find(id); // reload after Customer lock: another reviewer may already have completed it
        Status outcome = verify ? Status.VERIFIED : Status.REJECTED;
        if (!v.documentVersionId().equals(request.documentVersionId())) throw stale();
        if (verify) {
            if (request.rejectionReason() != null || request.presentedIdentityReference() == null || request.presentedIdentityReference().isBlank())
                throw new BusinessRuleViolationException("INVALID_IDENTITY_VERIFICATION_REQUEST", "Enter the Identity Reference shown on the evidence.");
            String presentedFingerprint = protector.protectIdentityReference(request.presentedIdentityReference()).fingerprint();
            if (!MessageDigest.isEqual(presentedFingerprint.getBytes(StandardCharsets.UTF_8), customer.profile().identityReference().fingerprint().getBytes(StandardCharsets.UTF_8)))
                throw new BusinessRuleViolationException("IDENTITY_REFERENCE_MISMATCH", "Presented Identity Reference does not match the Customer profile.");
        } else if (request.rejectionReason() == null || request.presentedIdentityReference() != null) {
            throw new BusinessRuleViolationException("INVALID_IDENTITY_VERIFICATION_REQUEST", "Select a controlled rejection reason.");
        }
        if (v.status() != Status.PENDING_REVIEW) {
            if (v.status() == outcome && Objects.equals(v.decisionRequestId(), request.requestId())
                    && Objects.equals(v.reviewedBy(), actor.userId()) && v.rejectionReason() == request.rejectionReason()) return dto(v, customer, true);
            throw new BusinessStateConflictException("IDENTITY_VERIFICATION_ALREADY_COMPLETED", "Identity verification has already completed.");
        }
        requireProfile(customer);
        if (!v.identityFullName().equals(customer.profile().fullName())) throw stale();
        evidence.requireCurrent(customer.id(), v.caseId(), v.documentVersionId());
        LocalDateTime now = now();
        var completed = verifications.save(v.complete(outcome, request.rejectionReason(), actor.userId(), request.requestId(), now));
        Customer saved = customers.save(customer.withVerificationStatus(verify ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED, now));
        audit(completed, actor, verify ? BusinessAuditAction.CUSTOMER_IDENTITY_VERIFIED : BusinessAuditAction.CUSTOMER_IDENTITY_REJECTED, now);
        return dto(completed, saved, true);
    }
    @Transactional(readOnly = true)
    public CustomerIdentityEvidencePort.Content readOwn(UUID id) {
        var actor = own("customer:identity:read:own"); var v = find(id);
        if (!actor.requireCustomerId().equals(v.customerId())) throw missing();
        return evidence.read(v.customerId(), v.caseId(), v.documentVersionId());
    }
    @Transactional(readOnly = true)
    public CustomerIdentityEvidencePort.Content readStaff(UUID id) {
        staff(); var v = find(id);
        return evidence.read(v.customerId(), v.caseId(), v.documentVersionId());
    }
    private CustomerIdentityVerificationDto dto(CustomerIdentityVerification v, Customer c, boolean detail) {
        return new CustomerIdentityVerificationDto(v.id(), v.sequence(), c.customerNumber(), v.identityFullName(),
                v.source().name(), "MANUAL_STAFF_DOCUMENT_REVIEW", v.status().name(), v.rejectionReason() == null ? null : v.rejectionReason().name(),
                v.submittedAt(), v.completedAt(), detail ? evidence.metadata(v.customerId(), v.caseId(), v.documentVersionId()) : null);
    }
    private Customer customer(UUID id, boolean lock) {
        return (lock ? customers.findByIdForUpdate(id) : customers.findById(id)).orElseThrow(CustomerIdentityVerificationService::missing);
    }
    private CustomerIdentityVerification find(UUID id) { return verifications.findById(id).orElseThrow(CustomerIdentityVerificationService::missing); }
    private void requireProfile(Customer c) {
        if (!c.isActive()) throw new BusinessStateConflictException("CUSTOMER_NOT_ACTIVE", "Customer must be active for this operation.");
        if (!c.hasCompleteProfile()) throw new BusinessRuleViolationException("PROFILE_INCOMPLETE", "Complete the Customer profile before submitting identity evidence.");
    }
    private AuthenticatedUser own(String permission) {
        var actor = users.currentUser();
        if (!"CUSTOMER".equals(actor.userType()) || actor.optionalCustomerId().isEmpty() || !actor.hasPermission(permission)) throw denied();
        return actor;
    }
    private AuthenticatedUser staff() {
        var actor = users.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent() || !actor.hasPermission("customer:identity:verify")) throw denied();
        return actor;
    }
    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private void audit(CustomerIdentityVerification v, AuthenticatedUser actor, BusinessAuditAction action, LocalDateTime now) {
        var payload = BusinessAuditPayload.builder().put(BusinessAuditPayloadKey.CUSTOMER_ID, v.customerId())
                .put(BusinessAuditPayloadKey.IDENTITY_VERIFICATION_ID, v.id()).put(BusinessAuditPayloadKey.DOCUMENT_VERSION_ID, v.documentVersionId())
                .put(BusinessAuditPayloadKey.IDENTITY_EVIDENCE_SOURCE, v.source()).put(BusinessAuditPayloadKey.IDENTITY_VERIFICATION_STATUS, v.status());
        if (v.rejectionReason() != null) payload.put(BusinessAuditPayloadKey.IDENTITY_REJECTION_REASON, v.rejectionReason());
        audits.publish(BusinessAuditEvent.single(BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now),
                new BusinessAuditEntry(action, BusinessAuditEntityType.CUSTOMER_IDENTITY_VERIFICATION, v.id(), payload.build())));
    }
    private static AuthorizationException denied() { return new AuthorizationException("IDENTITY_VERIFICATION_ACCESS_DENIED", "Customer identity verification access is denied."); }
    private static EntityNotFoundException missing() { return new EntityNotFoundException("IDENTITY_VERIFICATION_NOT_FOUND", "Identity verification was not found."); }
    private static BusinessStateConflictException stale() { return new BusinessStateConflictException("IDENTITY_VERIFICATION_EVIDENCE_STALE", "Identity evidence or Customer identity context has changed. Submit current evidence for review."); }
    private static BusinessStateConflictException notAllowed() { return new BusinessStateConflictException("IDENTITY_VERIFICATION_NOT_ALLOWED", "Customer identity is already verified."); }
}
