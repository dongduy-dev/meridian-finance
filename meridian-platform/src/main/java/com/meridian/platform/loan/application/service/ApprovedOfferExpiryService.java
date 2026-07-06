package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.ExpireApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApprovedOfferExpiryService implements ExpireApprovedOfferUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService;

    public ApprovedOfferExpiryService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            SalaryAdvanceReservationReleaseService salaryAdvanceReservationReleaseService
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.salaryAdvanceReservationReleaseService = salaryAdvanceReservationReleaseService;
    }

    @Override
    @Transactional
    public void expireDueOffer(UUID loanApplicationId, LocalDateTime now) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(now, "now must not be null");

        LoanApplication loanApplication = loanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));

        ApprovedOffer approvedOffer = approvedOfferRepository.findByLoanApplicationIdForUpdate(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND",
                        "Approved offer was not found for the loan application."
                ));

        if (approvedOffer.status() != ApprovedOfferStatus.PENDING || !approvedOffer.isExpiredAt(now)) {
            return;
        }

        ApprovedOffer expiredOffer = approvedOffer.expire(now);
        LoanApplication expiredApplication = loanApplication.expireApprovedOffer();
        approvedOfferRepository.save(expiredOffer);
        loanApplicationRepository.save(expiredApplication);
        salaryAdvanceReservationReleaseService.releaseReservationOnce(loanApplication, now);
    }
}
