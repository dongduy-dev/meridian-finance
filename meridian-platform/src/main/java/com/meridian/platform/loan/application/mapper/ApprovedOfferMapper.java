package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.dto.ProvisionalRepaymentItemDto;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ApprovedOfferMapper {

    public ApprovedOfferDto toDto(ApprovedOffer approvedOffer, LocalDateTime now) {
        ApprovedOfferStatus effectiveStatus = approvedOffer.effectiveStatusAt(now);
        return new ApprovedOfferDto(
                approvedOffer.id(),
                approvedOffer.loanApplicationId(),
                effectiveStatus.name(),
                approvedOffer.financialTerms().approvedPrincipal(),
                approvedOffer.financialTerms().approvedTermMonths(),
                approvedOffer.financialTerms().interestCalculationMethod().name(),
                approvedOffer.financialTerms().flatMonthlyInterestRate(),
                approvedOffer.financialTerms().totalInterest(),
                approvedOffer.financialTerms().feeAmount(),
                approvedOffer.financialTerms().totalRepaymentAmount(),
                approvedOffer.financialTerms().repaymentMethod().name(),
                approvedOffer.generatedAt(),
                approvedOffer.expiresAt(),
                approvedOffer.acceptedAt(),
                approvedOffer.declinedAt(),
                approvedOffer.expiredAt(),
                availableActions(effectiveStatus),
                approvedOffer.repaymentItems()
                        .stream()
                        .map(item -> new ProvisionalRepaymentItemDto(
                                item.installmentNumber(),
                                item.principalDue(),
                                item.interestDue(),
                                item.feeDue(),
                                item.totalDue(),
                                approvedOffer.financialTerms().repaymentMethod().name()
                        ))
                        .toList()
        );
    }

    private List<String> availableActions(ApprovedOfferStatus effectiveStatus) {
        if (effectiveStatus == ApprovedOfferStatus.PENDING) {
            return List.of("ACCEPT", "DECLINE");
        }
        return List.of();
    }
}
