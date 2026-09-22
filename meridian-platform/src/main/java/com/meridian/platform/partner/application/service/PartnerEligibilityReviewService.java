package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDecisionRequest;
import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewDto;
import com.meridian.platform.partner.application.dto.PartnerEligibilityReviewPageDto;
import com.meridian.platform.partner.application.port.in.DecidePartnerEligibilityReviewUseCase;
import com.meridian.platform.partner.application.port.in.QueryPartnerEligibilityReviewUseCase;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidenceSnapshot;
import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewDecision;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PartnerEligibilityReviewService
        implements QueryPartnerEligibilityReviewUseCase, DecidePartnerEligibilityReviewUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final PartnerEligibilityReviewRepository reviews;
    private final PartnerCompanyRepository companies;
    private final PartnerEmployeeImportBatchRepository importBatches;
    private final PartnerEmployeeRepository employees;
    private final CustomerPartnerEmployeeLinkRepository links;
    private final CustomerIdentityEvidencePort customerIdentityEvidence;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public PartnerEligibilityReviewService(
            PartnerEligibilityReviewRepository reviews,
            PartnerCompanyRepository companies,
            PartnerEmployeeImportBatchRepository importBatches,
            PartnerEmployeeRepository employees,
            CustomerPartnerEmployeeLinkRepository links,
            CustomerIdentityEvidencePort customerIdentityEvidence,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.reviews = reviews;
        this.companies = companies;
        this.importBatches = importBatches;
        this.employees = employees;
        this.links = links;
        this.customerIdentityEvidence = customerIdentityEvidence;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerEligibilityReviewPageDto queryReviews(String status, int page, int size) {
        requireAuthority(currentUserProvider.currentUser(), "partner:read");
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Partner eligibility review page arguments are invalid.");
        }
        PartnerEligibilityReviewStatus selectedStatus = parseStatus(status);
        PartnerEligibilityReviewRepository.Page selected = reviews.findPage(selectedStatus, page, size);
        return new PartnerEligibilityReviewPageDto(
                selected.page(), selected.size(), selected.totalElements(), selected.totalPages(),
                selected.reviews().stream().map(this::toItem).toList()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerEligibilityReviewDto queryReview(UUID reviewId) {
        requireAuthority(currentUserProvider.currentUser(), "partner:read");
        return toDetail(requireReview(reviewId));
    }

    @Override
    @Transactional
    public PartnerEligibilityReviewDto decide(
            UUID reviewId,
            PartnerEligibilityReviewDecisionRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireAuthority(actor, "partner:manage");

        PartnerEligibilityReviewRepository.LockIdentity lockIdentity = reviews.findLockIdentityById(reviewId)
                .orElseThrow(PartnerEligibilityReviewService::reviewNotFound);
        reviews.acquireCustomerPartnerLock(lockIdentity.customerId(), lockIdentity.partnerCompanyId());
        PartnerEligibilityReview review = reviews.findByIdForUpdate(reviewId)
                .orElseThrow(PartnerEligibilityReviewService::reviewNotFound);

        if (!review.isPending()) {
            if (isExactReplay(review, request)) {
                return toDetail(review);
            }
            throw new BusinessStateConflictException(
                    "PARTNER_ELIGIBILITY_REVIEW_ALREADY_RESOLVED",
                    "Partner eligibility review already has a terminal outcome."
            );
        }

        validateDecisionShape(request);
        LocalDateTime decidedAt = LocalDateTime.now(clock);
        PartnerCompany company = companies.findByIdForUpdate(review.partnerCompanyId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "PARTNER_COMPANY_NOT_FOUND", "Partner company was not found."
                ));
        validateCurrentReview(review, company);

        PartnerEligibilityReview resolved;
        if (request.outcome() == PartnerEligibilityReviewDecision.APPROVE) {
            CustomerIdentityEvidenceSnapshot identity = requireUsableIdentity(review.customerId());
            PartnerEmployee employee = requireApprovalCandidate(review, request.partnerEmployeeId(), identity);
            CustomerPartnerEmployeeLink link = links
                    .findCurrentByCustomerIdAndPartnerCompanyId(review.customerId(), review.partnerCompanyId())
                    .map(existing -> existing.approveManualReview(employee, identity.identityReference(), decidedAt))
                    .orElseGet(() -> CustomerPartnerEmployeeLink.manuallyApproved(
                            UUID.randomUUID(), review.customerId(), employee,
                            identity.identityReference(), decidedAt
                    ));
            links.save(link);
            resolved = review.approve(employee, request.reasonCode(), actor.userId(), decidedAt);
        } else {
            resolved = review.reject(request.reasonCode(), actor.userId(), decidedAt);
        }

        PartnerEligibilityReview saved = reviews.save(resolved);
        publishAudit(saved, actor, decidedAt);
        return toDetail(saved);
    }

    private void validateCurrentReview(PartnerEligibilityReview review, PartnerCompany company) {
        if (company.status() != PartnerCompanyStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "PARTNER_COMPANY_INACTIVE",
                    "Partner company is inactive for Salary Advance eligibility."
            );
        }
        String currentMonth = YearMonth.now(clock).toString();
        if (!review.effectiveMonth().equals(currentMonth)) {
            throw staleReview();
        }
        if (review.sourceImportBatchId() != null) {
            PartnerEmployeeImportBatch authoritative = importBatches
                    .findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(
                            review.partnerCompanyId(), currentMonth
                    )
                    .orElseThrow(PartnerEligibilityReviewService::staleReview);
            if (!authoritative.id().equals(review.sourceImportBatchId())) {
                throw staleReview();
            }
        }
    }

    private PartnerEmployee requireApprovalCandidate(
            PartnerEligibilityReview review,
            UUID partnerEmployeeId,
            CustomerIdentityEvidenceSnapshot identity
    ) {
        PartnerEmployeeImportBatch authoritative = importBatches
                .findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(
                        review.partnerCompanyId(), YearMonth.now(clock).toString()
                )
                .orElseThrow(PartnerEligibilityReviewService::staleReview);
        PartnerEmployee employee = employees.findById(partnerEmployeeId)
                .orElseThrow(PartnerEligibilityReviewService::invalidEmployee);
        if (!employee.partnerCompanyId().equals(review.partnerCompanyId())
                || !employee.importBatchId().equals(authoritative.id())) {
            throw invalidEmployee();
        }
        if (!employee.active() || employee.employmentStatus() != PartnerEmployeeStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "PARTNER_EMPLOYEE_INACTIVE",
                    "Selected Partner Employee is not active."
            );
        }
        if (!Objects.equals(employee.identityReference(), identity.identityReference())) {
            throw invalidEmployee();
        }
        return employee;
    }

    private CustomerIdentityEvidenceSnapshot requireUsableIdentity(UUID customerId) {
        CustomerIdentityEvidenceSnapshot identity = customerIdentityEvidence
                .findIdentityEvidenceByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CUSTOMER_NOT_FOUND", "Customer was not found."
                ));
        if (!identity.active()) {
            throw new BusinessStateConflictException(
                    "CUSTOMER_NOT_ACTIVE",
                    "Customer must be active before employee verification."
            );
        }
        if (!identity.profileComplete()
                || identity.identityReference() == null
                || identity.identityReference().isBlank()) {
            throw new BusinessRuleViolationException(
                    "PROFILE_INCOMPLETE",
                    "Customer profile must be complete before employee verification."
            );
        }
        return identity;
    }

    private PartnerEligibilityReviewPageDto.Item toItem(PartnerEligibilityReview review) {
        PartnerCompany company = companies.findById(review.partnerCompanyId())
                .orElseThrow(() -> new IllegalStateException("Review Partner Company is missing."));
        Availability availability = availability(review, false);
        return new PartnerEligibilityReviewPageDto.Item(
                review.id(), review.customerId(), company.id(), company.companyCode(), company.name(),
                review.effectiveMonth(), review.triggerOutcome().name(), review.requestedEmployeeCode(),
                review.status().name(), review.createdAt(), availability.rejectionAvailable(),
                availability.reason()
        );
    }

    private PartnerEligibilityReviewDto toDetail(PartnerEligibilityReview review) {
        PartnerCompany company = companies.findById(review.partnerCompanyId())
                .orElseThrow(() -> new IllegalStateException("Review Partner Company is missing."));
        Availability availability = availability(review, true);
        PartnerEligibilityReviewDto.EmployeeCandidate selected = review.selectedPartnerEmployeeId() == null
                ? null
                : employees.findById(review.selectedPartnerEmployeeId()).map(this::candidate).orElse(null);
        return new PartnerEligibilityReviewDto(
                review.id(), review.customerId(),
                new PartnerEligibilityReviewDto.PartnerCompanySummary(
                        company.id(), company.companyCode(), company.name(), company.status().name()
                ),
                review.effectiveMonth(), review.sourceImportBatchId(), review.triggerOutcome().name(),
                review.requestedEmployeeCode(), review.status().name(),
                review.decisionOutcome() == null ? null : review.decisionOutcome().name(),
                review.decisionReason() == null ? null : review.decisionReason().name(),
                selected, review.reviewerUserId(), review.reviewedAt(), review.createdAt(), review.updatedAt(),
                availability.candidates(), availability.approvalAvailable(), availability.rejectionAvailable(),
                availability.reason()
        );
    }

    private Availability availability(PartnerEligibilityReview review, boolean includeCandidates) {
        if (!review.isPending()) {
            return Availability.unavailable("REVIEW_RESOLVED");
        }
        if (!review.effectiveMonth().equals(YearMonth.now(clock).toString())) {
            return Availability.unavailable("PRIOR_EFFECTIVE_MONTH");
        }
        Optional<PartnerCompany> company = companies.findById(review.partnerCompanyId());
        if (company.isEmpty() || company.orElseThrow().status() != PartnerCompanyStatus.ACTIVE) {
            return Availability.unavailable("PARTNER_COMPANY_INACTIVE");
        }
        Optional<PartnerEmployeeImportBatch> batch = importBatches
                .findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(
                        review.partnerCompanyId(), review.effectiveMonth()
                );
        if (review.sourceImportBatchId() != null
                && (batch.isEmpty() || !batch.orElseThrow().id().equals(review.sourceImportBatchId()))) {
            return Availability.unavailable("SOURCE_BATCH_REPLACED");
        }
        if (!includeCandidates) {
            return new Availability(batch.isPresent(), true, null, List.of());
        }
        Optional<CustomerIdentityEvidenceSnapshot> identity = customerIdentityEvidence
                .findIdentityEvidenceByCustomerId(review.customerId())
                .filter(CustomerIdentityEvidenceSnapshot::active)
                .filter(CustomerIdentityEvidenceSnapshot::profileComplete)
                .filter(value -> value.identityReference() != null && !value.identityReference().isBlank());
        if (identity.isEmpty()) {
            return Availability.unavailable("CUSTOMER_IDENTITY_EVIDENCE_UNAVAILABLE");
        }
        List<PartnerEligibilityReviewDto.EmployeeCandidate> candidates = batch
                .map(value -> employees.findByIdentityEvidence(
                        review.partnerCompanyId(), value.id(), identity.orElseThrow().identityReference()
                ).stream().map(this::candidate).toList())
                .orElseGet(List::of);
        boolean approvalAvailable = candidates.stream().anyMatch(candidate ->
                candidate.active() && "ACTIVE".equals(candidate.employmentStatus()));
        return new Availability(approvalAvailable, true, null, candidates);
    }

    private PartnerEligibilityReviewDto.EmployeeCandidate candidate(PartnerEmployee employee) {
        return new PartnerEligibilityReviewDto.EmployeeCandidate(
                employee.id(), employee.importBatchId(), employee.employeeCode(),
                employee.employmentStatus().name(), employee.active()
        );
    }

    private void publishAudit(
            PartnerEligibilityReview review,
            AuthenticatedUser actor,
            LocalDateTime occurredAt
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.PARTNER_ELIGIBILITY_REVIEW_ID, review.id())
                .put(BusinessAuditPayloadKey.PARTNER_COMPANY_ID, review.partnerCompanyId())
                .put(BusinessAuditPayloadKey.PARTNER_ELIGIBILITY_REVIEW_OUTCOME, review.decisionOutcome())
                .put(BusinessAuditPayloadKey.PARTNER_ELIGIBILITY_REVIEW_REASON, review.decisionReason());
        if (review.selectedPartnerEmployeeId() != null) {
            payload.put(BusinessAuditPayloadKey.PARTNER_EMPLOYEE_ID, review.selectedPartnerEmployeeId())
                    .put(BusinessAuditPayloadKey.PARTNER_EMPLOYEE_IMPORT_BATCH_ID, review.selectedImportBatchId());
        }
        BusinessAuditAction action = review.status() == PartnerEligibilityReviewStatus.APPROVED
                ? BusinessAuditAction.PARTNER_ELIGIBILITY_REVIEW_APPROVED
                : BusinessAuditAction.PARTNER_ELIGIBILITY_REVIEW_REJECTED;
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), occurredAt),
                new BusinessAuditEntry(
                        action, BusinessAuditEntityType.PARTNER_ELIGIBILITY_REVIEW,
                        review.id(), payload.build()
                )
        ));
    }

    private PartnerEligibilityReview requireReview(UUID reviewId) {
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        return reviews.findById(reviewId).orElseThrow(PartnerEligibilityReviewService::reviewNotFound);
    }

    private static PartnerEligibilityReviewStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return PartnerEligibilityReviewStatus.PENDING;
        }
        try {
            return PartnerEligibilityReviewStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Partner eligibility review status is invalid.");
        }
    }

    private static void validateDecisionShape(PartnerEligibilityReviewDecisionRequest request) {
        if (!request.reasonCode().supports(request.outcome())) {
            throw new IllegalArgumentException("Review reason does not support the selected outcome.");
        }
        if (request.outcome() == PartnerEligibilityReviewDecision.APPROVE
                && request.partnerEmployeeId() == null) {
            throw new IllegalArgumentException("Approval requires one Partner Employee.");
        }
        if (request.outcome() == PartnerEligibilityReviewDecision.REJECT
                && request.partnerEmployeeId() != null) {
            throw new IllegalArgumentException("Rejection must not select a Partner Employee.");
        }
    }

    private static boolean isExactReplay(
            PartnerEligibilityReview review,
            PartnerEligibilityReviewDecisionRequest request
    ) {
        return switch (review.status()) {
            case APPROVED -> request.outcome() == PartnerEligibilityReviewDecision.APPROVE
                    && Objects.equals(review.selectedPartnerEmployeeId(), request.partnerEmployeeId())
                    && review.decisionReason() == request.reasonCode();
            case REJECTED -> request.outcome() == PartnerEligibilityReviewDecision.REJECT
                    && request.partnerEmployeeId() == null
                    && review.decisionReason() == request.reasonCode();
            case PENDING, SUPERSEDED -> false;
        };
    }

    private static void requireAuthority(AuthenticatedUser actor, String permission) {
        if (!"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(permission)) {
            throw new AuthorizationException(
                    "PARTNER_ELIGIBILITY_REVIEW_ACCESS_DENIED",
                    "Partner eligibility review access is denied."
            );
        }
    }

    private static EntityNotFoundException reviewNotFound() {
        return new EntityNotFoundException(
                "PARTNER_ELIGIBILITY_REVIEW_NOT_FOUND",
                "Partner eligibility review was not found."
        );
    }

    private static BusinessStateConflictException staleReview() {
        return new BusinessStateConflictException(
                "PARTNER_ELIGIBILITY_REVIEW_STALE",
                "Partner eligibility review evidence is stale; fresh Customer verification is required."
        );
    }

    private static BusinessRuleViolationException invalidEmployee() {
        return new BusinessRuleViolationException(
                "PARTNER_ELIGIBILITY_EMPLOYEE_INVALID",
                "Selected Partner Employee is not valid current evidence for this review."
        );
    }

    private record Availability(
            boolean approvalAvailable,
            boolean rejectionAvailable,
            String reason,
            List<PartnerEligibilityReviewDto.EmployeeCandidate> candidates
    ) {
        private static Availability unavailable(String reason) {
            return new Availability(false, false, reason, List.of());
        }
    }
}
