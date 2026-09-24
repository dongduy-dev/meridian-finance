package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationRequest;
import com.meridian.platform.partner.application.mapper.PartnerEmployeeVerificationMapper;
import com.meridian.platform.partner.application.port.in.VerifyPartnerEmployeeUseCase;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidencePort;
import com.meridian.platform.partner.application.port.out.CustomerIdentityEvidenceSnapshot;
import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeVerificationResult;
import com.meridian.platform.partner.domain.service.PartnerEmployeeVerificationPolicy;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class VerifyPartnerEmployeeService implements VerifyPartnerEmployeeUseCase {

    private final PartnerCompanyRepository partnerCompanyRepository;
    private final PartnerEmployeeImportBatchRepository importBatchRepository;
    private final PartnerEmployeeRepository partnerEmployeeRepository;
    private final CustomerPartnerEmployeeLinkRepository linkRepository;
    private final PartnerEligibilityReviewRepository reviewRepository;
    private final CustomerIdentityEvidencePort customerIdentityEvidencePort;
    private final PartnerEmployeeVerificationMapper verificationMapper;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final PartnerEmployeeVerificationPolicy verificationPolicy = new PartnerEmployeeVerificationPolicy();

    public VerifyPartnerEmployeeService(
            PartnerCompanyRepository partnerCompanyRepository,
            PartnerEmployeeImportBatchRepository importBatchRepository,
            PartnerEmployeeRepository partnerEmployeeRepository,
            CustomerPartnerEmployeeLinkRepository linkRepository,
            PartnerEligibilityReviewRepository reviewRepository,
            CustomerIdentityEvidencePort customerIdentityEvidencePort,
            PartnerEmployeeVerificationMapper verificationMapper,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.importBatchRepository = importBatchRepository;
        this.partnerEmployeeRepository = partnerEmployeeRepository;
        this.linkRepository = linkRepository;
        this.reviewRepository = reviewRepository;
        this.customerIdentityEvidencePort = customerIdentityEvidencePort;
        this.verificationMapper = verificationMapper;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PartnerEmployeeVerificationDto verifyPartnerEmployee(
            UUID partnerCompanyId,
            PartnerEmployeeVerificationRequest request
    ) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        UUID customerId = currentUserProvider.currentUser().requireCustomerId();
        CustomerIdentityEvidenceSnapshot identityEvidence = loadUsableIdentityEvidence(customerId);
        String identityReference = normalizeRequired(identityEvidence.identityReference(), "identityReference");
        String employeeCode = normalizeRequired(request.employeeCode(), "employeeCode");

        linkRepository.acquireCustomerEmploymentLock(customerId);
        reviewRepository.acquireCustomerPartnerLock(customerId, partnerCompanyId);

        PartnerCompany partnerCompany = partnerCompanyRepository.findByIdForUpdate(partnerCompanyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PARTNER_COMPANY_NOT_FOUND",
                        "Partner company was not found."
                ));
        verificationPolicy.validatePartnerCompanyCanBeUsedForEligibility(partnerCompany);

        String effectiveMonth = YearMonth.now(clock).toString();
        PartnerEmployeeImportBatch importBatch = importBatchRepository
                .findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(partnerCompanyId, effectiveMonth)
                .orElse(null);
        PartnerEmployeeVerificationDto result = importBatch == null
                ? verificationMapper.toDto(new PartnerEmployeeVerificationResult(
                        customerId, partnerCompanyId, null, null,
                        EmployeeVerificationOutcome.PENDING_MANUAL_REVIEW, null, true, null, null
                ))
                : verifyAgainstBatch(
                        customerId,
                        partnerCompanyId,
                        importBatch,
                        identityReference,
                        employeeCode
                );

        if (result.manualReviewRequired()) {
            ensurePendingReview(
                    customerId, partnerCompanyId, effectiveMonth,
                    importBatch == null ? null : importBatch.id(),
                    EmployeeVerificationOutcome.valueOf(result.outcome()), employeeCode
            );
        } else if (EmployeeVerificationOutcome.MATCHED_ACTIVE.name().equals(result.outcome())
                || EmployeeVerificationOutcome.MATCHED_INACTIVE.name().equals(result.outcome())) {
            supersedePendingReview(customerId, partnerCompanyId);
        }
        return result;
    }

    private CustomerIdentityEvidenceSnapshot loadUsableIdentityEvidence(UUID customerId) {
        CustomerIdentityEvidenceSnapshot identityEvidence = customerIdentityEvidencePort.findIdentityEvidenceByCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer was not found."
                ));
        if (!identityEvidence.active()) {
            throw new BusinessStateConflictException(
                    "CUSTOMER_NOT_ACTIVE",
                    "Customer must be active before employee verification."
            );
        }
        if (!identityEvidence.profileComplete()) {
            throw new BusinessRuleViolationException(
                    "PROFILE_INCOMPLETE",
                    "Customer profile must be complete before employee verification."
            );
        }
        return identityEvidence;
    }

    private PartnerEmployeeVerificationDto verifyAgainstBatch(
            UUID customerId,
            UUID partnerCompanyId,
            PartnerEmployeeImportBatch importBatch,
            String identityReference,
            String employeeCode
    ) {
        List<PartnerEmployee> matchingEmployees = partnerEmployeeRepository.findByVerificationEvidence(
                partnerCompanyId,
                importBatch.id(),
                identityReference,
                employeeCode
        );

        EmployeeVerificationOutcome outcome = verificationPolicy.determineOutcome(matchingEmployees);
        if (outcome == EmployeeVerificationOutcome.MATCHED_ACTIVE) {
            return verificationMapper.toDto(verifyActiveMatch(
                    customerId,
                    partnerCompanyId,
                    matchingEmployees.get(0),
                    identityReference,
                    employeeCode
            ));
        }

        PartnerEmployee matchedEmployee = matchingEmployees.size() == 1 ? matchingEmployees.get(0) : null;
        return verificationMapper.toDto(new PartnerEmployeeVerificationResult(
                customerId,
                partnerCompanyId,
                matchedEmployee == null ? null : matchedEmployee.id(),
                null,
                outcome,
                null,
                verificationPolicy.requiresManualReview(outcome),
                null,
                null
        ));
    }

    private PartnerEmployeeVerificationResult verifyActiveMatch(
            UUID customerId,
            UUID partnerCompanyId,
            PartnerEmployee partnerEmployee,
            String identityReference,
            String employeeCode
    ) {
        LocalDateTime verifiedAt = LocalDateTime.now(clock);

        CustomerPartnerEmployeeLink current = linkRepository
                .findCurrentVerifiedByCustomerIdForUpdate(customerId)
                .orElse(null);
        if (current == null) {
            return createVerifiedLink(
                    customerId, partnerCompanyId, partnerEmployee,
                    identityReference, employeeCode, verifiedAt
            );
        }
        if (current.partnerCompanyId().equals(partnerCompanyId)) {
            return handleExistingLink(
                    current, partnerEmployee, identityReference, employeeCode, verifiedAt
            );
        }

        linkRepository.saveAndFlush(current.disableForEmploymentChange());
        return createVerifiedLink(
                customerId, partnerCompanyId, partnerEmployee,
                identityReference, employeeCode, verifiedAt
        );
    }

    private PartnerEmployeeVerificationResult handleExistingLink(
            CustomerPartnerEmployeeLink existingLink,
            PartnerEmployee partnerEmployee,
            String identityReference,
            String employeeCode,
            LocalDateTime verifiedAt
    ) {
        if (!existingLink.isVerified() || !existingLink.hasSameVerifiedEvidence(identityReference, employeeCode)) {
            return new PartnerEmployeeVerificationResult(
                    existingLink.customerId(),
                    existingLink.partnerCompanyId(),
                    partnerEmployee.id(),
                    existingLink.id(),
                    EmployeeVerificationOutcome.PENDING_MANUAL_REVIEW,
                    existingLink.linkStatus(),
                    true,
                    null,
                    null
            );
        }

        CustomerPartnerEmployeeLink refreshedLink = existingLink.refreshVerifiedLink(
                partnerEmployee,
                identityReference,
                employeeCode,
                verifiedAt
        );

        CustomerPartnerEmployeeLink savedLink = linkRepository.save(refreshedLink);
        return matchedActiveResult(savedLink, partnerEmployee);
    }

    private PartnerEmployeeVerificationResult createVerifiedLink(
            UUID customerId,
            UUID partnerCompanyId,
            PartnerEmployee partnerEmployee,
            String identityReference,
            String employeeCode,
            LocalDateTime verifiedAt
    ) {
        CustomerPartnerEmployeeLink link = CustomerPartnerEmployeeLink.verified(
                UUID.randomUUID(),
                customerId,
                partnerEmployee,
                identityReference,
                employeeCode,
                verifiedAt
        );

        CustomerPartnerEmployeeLink savedLink = linkRepository.save(link);
        return matchedActiveResult(savedLink, partnerEmployee);
    }

    private PartnerEmployeeVerificationResult matchedActiveResult(
            CustomerPartnerEmployeeLink link,
            PartnerEmployee partnerEmployee
    ) {
        return new PartnerEmployeeVerificationResult(
                link.customerId(),
                link.partnerCompanyId(),
                partnerEmployee.id(),
                link.id(),
                EmployeeVerificationOutcome.MATCHED_ACTIVE,
                link.linkStatus(),
                false,
                partnerEmployee.salaryAmount(),
                partnerEmployee.salaryAdvanceLimit()
        );
    }

    private String normalizeRequired(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private void ensurePendingReview(
            UUID customerId,
            UUID partnerCompanyId,
            String effectiveMonth,
            UUID sourceImportBatchId,
            EmployeeVerificationOutcome triggerOutcome,
            String requestedEmployeeCode
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        PartnerEligibilityReview existing = reviewRepository
                .findPendingByCustomerIdAndPartnerCompanyId(customerId, partnerCompanyId)
                .orElse(null);
        if (existing != null
                && existing.effectiveMonth().equals(effectiveMonth)
                && (existing.sourceImportBatchId() == null
                    || Objects.equals(existing.sourceImportBatchId(), sourceImportBatchId))) {
            return;
        }
        if (existing != null) {
            reviewRepository.save(existing.supersede(now));
        }
        reviewRepository.save(PartnerEligibilityReview.pending(
                UUID.randomUUID(), customerId, partnerCompanyId, effectiveMonth,
                sourceImportBatchId, triggerOutcome, requestedEmployeeCode, now
        ));
    }

    private void supersedePendingReview(UUID customerId, UUID partnerCompanyId) {
        reviewRepository.findPendingByCustomerIdAndPartnerCompanyId(customerId, partnerCompanyId)
                .ifPresent(review -> reviewRepository.save(review.supersede(LocalDateTime.now(clock))));
    }
}
