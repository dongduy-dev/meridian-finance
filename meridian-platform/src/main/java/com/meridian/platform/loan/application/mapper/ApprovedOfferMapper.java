package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.ApprovedOfferDto;
import com.meridian.platform.loan.application.dto.ProvisionalRepaymentItemDto;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ApprovedOfferMapper {

    public ApprovedOfferDto toDto(ApprovedOffer approvedOffer, LocalDateTime now) {
        return toDto(approvedOffer, now, true);
    }

    public ApprovedOfferDto toCustomerDto(ApprovedOffer approvedOffer, LocalDateTime now,
            OriginationChannel originationChannel) {
        return toDto(approvedOffer, now, originationChannel == OriginationChannel.CUSTOMER_DIGITAL);
    }

    private ApprovedOfferDto toDto(ApprovedOffer approvedOffer, LocalDateTime now, boolean directActionsAllowed) {
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
                directActionsAllowed ? availableActions(effectiveStatus) : List.of(),
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
