package com.meridian.platform.loan.domain.service.salaryadvance;

import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.ProvisionalRepaymentItem;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceOfferPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SalaryAdvanceOfferCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public ApprovedOffer generate(
            UUID offerId,
            UUID loanApplicationId,
            SalaryAdvanceOfferPolicy policy,
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
                repaymentItems(approvedPrincipal, totalInterest, totalFee, approvedTermMonths),
                generatedAt,
                generatedAt.plusDays(policy.offerValidityDays())
        );
    }

    private List<ProvisionalRepaymentItem> repaymentItems(
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal fee,
            int termMonths
    ) {
        List<BigDecimal> principalParts = splitWholeVnd(principal, termMonths);
        List<BigDecimal> interestParts = splitWholeVnd(interest, termMonths);
        List<BigDecimal> feeParts = splitWholeVnd(fee, termMonths);

        List<ProvisionalRepaymentItem> items = new ArrayList<>();
        for (int index = 0; index < termMonths; index++) {
            items.add(ProvisionalRepaymentItem.of(
                    UUID.randomUUID(),
                    index + 1,
                    principalParts.get(index),
                    interestParts.get(index),
                    feeParts.get(index)
            ));
        }
        return items;
    }

    private List<BigDecimal> splitWholeVnd(BigDecimal amount, int parts) {
        BigDecimal wholeAmount = amount.setScale(0, RoundingMode.UNNECESSARY);
        BigDecimal base = wholeAmount.divideToIntegralValue(BigDecimal.valueOf(parts));
        BigDecimal remainder = wholeAmount.remainder(BigDecimal.valueOf(parts));

        List<BigDecimal> result = new ArrayList<>();
        for (int index = 1; index <= parts; index++) {
            BigDecimal value = base;
            if (index == parts) {
                value = value.add(remainder);
            }
            result.add(value.setScale(2));
        }
        if (result.isEmpty()) {
            return List.of(ZERO);
        }
        return result;
    }
}
