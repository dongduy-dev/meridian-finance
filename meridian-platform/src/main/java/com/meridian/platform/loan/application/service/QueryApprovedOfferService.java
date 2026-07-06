package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.port.in.QueryApprovedOfferUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryApprovedOfferService implements QueryApprovedOfferUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApprovedOfferRepository approvedOfferRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovedOfferMapper approvedOfferMapper;
    private final Clock clock;

    public QueryApprovedOfferService(
            LoanApplicationRepository loanApplicationRepository,
            ApprovedOfferRepository approvedOfferRepository,
            CurrentUserProvider currentUserProvider,
            ApprovedOfferMapper approvedOfferMapper,
            Clock clock
    ) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.approvedOfferRepository = approvedOfferRepository;
        this.currentUserProvider = currentUserProvider;
        this.approvedOfferMapper = approvedOfferMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovedOfferDto getApprovedOffer(UUID loanApplicationId) {
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");

        UUID customerId = currentUserProvider.currentUser().requireCustomerId();
        LoanApplication loanApplication = loanApplicationRepository.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND",
                        "Loan application was not found."
                ));
        assertOwnApplication(loanApplication, customerId);

        ApprovedOffer approvedOffer = approvedOfferRepository.findByLoanApplicationId(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "APPROVED_OFFER_NOT_FOUND",
                        "Approved offer was not found for the loan application."
                ));

        return approvedOfferMapper.toDto(approvedOffer, LocalDateTime.now(clock));
    }

    private void assertOwnApplication(LoanApplication loanApplication, UUID customerId) {
        if (!loanApplication.customerId().equals(customerId)) {
            throw new AuthorizationException(
                    "ACCESS_DENIED",
                    "Loan application does not belong to the authenticated customer."
            );
        }
    }
}
