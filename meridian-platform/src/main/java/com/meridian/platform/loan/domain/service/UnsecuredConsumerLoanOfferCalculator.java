package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.ProvisionalRepaymentItem;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanOfferPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class UnsecuredConsumerLoanOfferCalculator {

    public ApprovedOffer generate(
            UUID offerId,
            UUID loanApplicationId,
            UnsecuredConsumerLoanOfferPolicy policy,
            BigDecimal approvedPrincipal,
            int approvedTermMonths,
            LocalDateTime generatedAt
    ) {
        Objects.requireNonNull(offerId, "offerId must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(approvedPrincipal, "approvedPrincipal must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");

        policy.validateApprovedTerm(approvedTermMonths);

        BigDecimal totalInterest = approvedPrincipal
                .multiply(policy.flatMonthlyInterestRate())
                .multiply(BigDecimal.valueOf(approvedTermMonths))
                .setScale(0, RoundingMode.HALF_UP)
                .setScale(2);
        BigDecimal totalFee = policy.feeAmount().setScale(2);
        BigDecimal totalRepayment = approvedPrincipal.add(totalInterest).add(totalFee);

        ApprovedOfferFinancialTerms financialTerms = new ApprovedOfferFinancialTerms(
                approvedPrincipal,
                approvedTermMonths,
                policy.interestCalculationMethod(),
                policy.flatMonthlyInterestRate(),
                totalInterest,
                totalFee,
                totalRepayment,
                policy.repaymentMethod()
        );

        return ApprovedOffer.pending(
                offerId,
                loanApplicationId,
                policy.id(),
                financialTerms,
                repaymentItems(approvedPrincipal, totalInterest, approvedTermMonths),
                generatedAt,
                generatedAt.plusDays(policy.offerValidityDays())
        );
    }

    private List<ProvisionalRepaymentItem> repaymentItems(
            BigDecimal principal,
            BigDecimal interest,
            int termMonths
    ) {
        List<BigDecimal> principalParts = splitWholeVnd(principal, termMonths);
        List<BigDecimal> interestParts = splitWholeVnd(interest, termMonths);
        List<ProvisionalRepaymentItem> items = new ArrayList<>();

        for (int index = 0; index < termMonths; index++) {
            items.add(ProvisionalRepaymentItem.of(
                    UUID.randomUUID(),
                    index + 1,
                    principalParts.get(index),
                    interestParts.get(index),
                    BigDecimal.ZERO.setScale(2)
            ));
        }
        return items;
    }

    private List<BigDecimal> splitWholeVnd(BigDecimal amount, int parts) {
        BigDecimal wholeAmount = amount.setScale(0, RoundingMode.UNNECESSARY);
        BigDecimal divisor = BigDecimal.valueOf(parts);
        BigDecimal base = wholeAmount.divideToIntegralValue(divisor);
        BigDecimal remainder = wholeAmount.remainder(divisor);
        List<BigDecimal> result = new ArrayList<>();

        for (int index = 1; index <= parts; index++) {
            result.add((index == parts ? base.add(remainder) : base).setScale(2));
        }
        return result;
    }
}
