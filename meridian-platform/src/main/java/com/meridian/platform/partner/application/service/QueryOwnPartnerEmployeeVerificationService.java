package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.OwnPartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.port.in.QueryOwnPartnerEmployeeVerificationUseCase;
import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReview;
import com.meridian.platform.partner.domain.model.PartnerEligibilityReviewStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryOwnPartnerEmployeeVerificationService
        implements QueryOwnPartnerEmployeeVerificationUseCase {

    private final PartnerEligibilityReviewRepository reviews;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public QueryOwnPartnerEmployeeVerificationService(
            PartnerEligibilityReviewRepository reviews,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.reviews = reviews;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnPartnerEmployeeVerificationDto> getCurrentOwnVerifications() {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        if (!"CUSTOMER".equals(actor.userType())) {
            throw new AuthorizationException(
                    "CUSTOMER_CONTEXT_REQUIRED",
                    "Authenticated user is not linked to a customer profile."
            );
        }
        UUID customerId = actor.requireCustomerId();
        String effectiveMonth = YearMonth.now(clock).toString();
        return reviews.findCurrentLatestByCustomerIdAndEffectiveMonth(customerId, effectiveMonth).stream()
                .map(QueryOwnPartnerEmployeeVerificationService::toCustomerSafeState)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<OwnPartnerEmployeeVerificationDto> toCustomerSafeState(
            PartnerEligibilityReview review
    ) {
        if (review.status() == PartnerEligibilityReviewStatus.SUPERSEDED) {
            return Optional.empty();
        }
        EmployeeVerificationOutcome outcome = review.status() == PartnerEligibilityReviewStatus.PENDING
                ? EmployeeVerificationOutcome.PENDING_MANUAL_REVIEW
                : review.decisionOutcome();
        if (outcome == null) {
            return Optional.empty();
        }
        return Optional.of(new OwnPartnerEmployeeVerificationDto(
                review.partnerCompanyId(),
                outcome.name(),
                review.status() == PartnerEligibilityReviewStatus.PENDING
        ));
    }
}
