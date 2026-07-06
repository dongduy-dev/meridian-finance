package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApprovedOfferActionOutcome;
import com.meridian.platform.loan.application.dto.ApprovedOfferActionResult;
import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.port.in.RespondToApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class RespondToApprovedOfferService implements RespondToApprovedOfferUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;
    private final ApprovedOfferMapper approvedOfferMapper;
    private final Clock clock;

    public RespondToApprovedOfferService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            CurrentUserProvider currentUserProvider,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService,
            ApprovedOfferMapper approvedOfferMapper,
            Clock clock
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.currentUserProvider = currentUserProvider;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
        this.approvedOfferMapper = approvedOfferMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApprovedOfferActionResult acceptOffer(UUID loanApplicationId) {
        return respond(loanApplicationId, OfferResponseAction.ACCEPT);
    }

    @Override
    @Transactional
    public ApprovedOfferActionResult declineOffer(UUID loanApplicationId) {
        return respond(loanApplicationId, OfferResponseAction.DECLINE);
    }

    private ApprovedOfferActionResult respond(UUID loanApplicationId, OfferResponseAction action) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(action, "action must not be null");

        UUID customerId = currentUserProvider.currentUser().requireCustomerId();
        LocalDateTime now = LocalDateTime.now(clock);
        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        assertOwnApplication(loanApplication, customerId);

        ApprovedOffer approvedOffer = approvedOfferRepository.findByLoanApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND",
                        "Approved offer was not found for the loan application."
                ));

        if (isIdempotentCurrentResult(approvedOffer, action)) {
            return success(approvedOffer, now);
        }
        if (approvedOffer.status() != ApprovedOfferStatus.PENDING) {
            throw offerActionConflict();
        }
        if (approvedOffer.isExpiredAt(now)) {
            ApprovedOffer expiredOffer = approvedOffer.expire(now);
            LoanApplication expiredApplication = loanApplication.expireApprovedOffer();
            ApprovedOffer savedOffer = approvedOfferRepository.save(expiredOffer);
            loanApplicationRepository.save(expiredApplication);
            salaryAdvanceReservationReleaseService.releaseReservationOnce(loanApplication, now);
            return new ApprovedOfferActionResult(
                    ApprovedOfferActionOutcome.EXPIRED,
                    approvedOfferMapper.toDto(savedOffer, now)
            );
        }

        if (action == OfferResponseAction.ACCEPT) {
            ApprovedOffer acceptedOffer = approvedOffer.accept(now);
            LoanApplication acceptedApplication = loanApplication.acceptApprovedOffer();
            ApprovedOffer savedOffer = approvedOfferRepository.save(acceptedOffer);
            loanApplicationRepository.save(acceptedApplication);
            return success(savedOffer, now);
        }

        ApprovedOffer declinedOffer = approvedOffer.decline(now);
        LoanApplication declinedApplication = loanApplication.declineApprovedOffer();
        ApprovedOffer savedOffer = approvedOfferRepository.save(declinedOffer);
        loanApplicationRepository.save(declinedApplication);
        salaryAdvanceReservationReleaseService.releaseReservationOnce(loanApplication, now);
        return success(savedOffer, now);
    }

    private boolean isIdempotentCurrentResult(ApprovedOffer approvedOffer, OfferResponseAction action) {
        return (action == OfferResponseAction.ACCEPT && approvedOffer.status() == ApprovedOfferStatus.ACCEPTED)
                || (action == OfferResponseAction.DECLINE && approvedOffer.status() == ApprovedOfferStatus.DECLINED);
    }

    private ApprovedOfferActionResult success(ApprovedOffer approvedOffer, LocalDateTime now) {
        return new ApprovedOfferActionResult(
                ApprovedOfferActionOutcome.SUCCESS,
                approvedOfferMapper.toDto(approvedOffer, now)
        );
    }

    private void assertOwnApplication(LoanApplication loanApplication, UUID customerId) {
        if (!loanApplication.customerId().equals(customerId)) {
            throw new AuthorizationException(
                    "ACCESS_DENIED",
                    "Loan application does not belong to the authenticated customer."
            );
        }
    }

    private BusinessStateConflictException offerActionConflict() {
        return new BusinessStateConflictException(
                "OFFER_ACTION_CONFLICT",
                "Approved offer action conflicts with the current offer state."
        );
    }

    private enum OfferResponseAction {
        ACCEPT,
        DECLINE
    }
}
