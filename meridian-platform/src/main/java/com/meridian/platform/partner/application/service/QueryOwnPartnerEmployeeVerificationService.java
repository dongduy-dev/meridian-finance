package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.OwnPartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.port.in.QueryOwnPartnerEmployeeVerificationUseCase;
import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class QueryOwnPartnerEmployeeVerificationService
        implements QueryOwnPartnerEmployeeVerificationUseCase {

    private final PartnerEligibilityReviewRepository reviews;
    private final CurrentUserProvider currentUserProvider;

    public QueryOwnPartnerEmployeeVerificationService(
            PartnerEligibilityReviewRepository reviews,
            CurrentUserProvider currentUserProvider
    ) {
        this.reviews = reviews;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnPartnerEmployeeVerificationDto getLatestOwnVerification(UUID partnerCompanyId) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");
        UUID customerId = currentUserProvider.currentUser().requireCustomerId();
        PartnerEligibilityReview review = reviews
                .findLatestByCustomerIdAndPartnerCompanyId(customerId, partnerCompanyId)
                .filter(value -> value.status() != PartnerEligibilityReviewStatus.SUPERSEDED)
                .orElseThrow(QueryOwnPartnerEmployeeVerificationService::verificationNotFound);
        EmployeeVerificationOutcome outcome = review.status() == PartnerEligibilityReviewStatus.PENDING
                ? EmployeeVerificationOutcome.PENDING_MANUAL_REVIEW
                : review.decisionOutcome();
        if (outcome == null) {
            throw verificationNotFound();
        }
        return new OwnPartnerEmployeeVerificationDto(
                review.partnerCompanyId(),
                outcome.name(),
                review.status() == PartnerEligibilityReviewStatus.PENDING
        );
    }

    private static EntityNotFoundException verificationNotFound() {
        return new EntityNotFoundException(
                "PARTNER_EMPLOYEE_VERIFICATION_NOT_FOUND",
                "Employee verification state was not found."
        );
    }
}
