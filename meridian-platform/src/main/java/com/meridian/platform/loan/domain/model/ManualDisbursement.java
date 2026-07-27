package com.meridian.platform.loan.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ManualDisbursement(
        UUID id,
        UUID loanApplicationId,
        UUID loanContractId,
        UUID loanAccountId,
        UUID requestId,
        int expectedContractVersion,
        String externalTransferReference,
        BigDecimal disbursedAmount,
        LocalDate valueDate,
        LocalDate firstRepaymentDate,
        UUID confirmedByUserId,
        LocalDateTime confirmedAt
) {

    private static final Pattern EXTERNAL_REFERENCE_PATTERN =
            Pattern.compile("[A-Z0-9][A-Z0-9._:/-]{0,63}");

    public ManualDisbursement {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(loanApplicationId, "loanApplicationId must not be null");
        Objects.requireNonNull(loanContractId, "loanContractId must not be null");
        Objects.requireNonNull(loanAccountId, "loanAccountId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(disbursedAmount, "disbursedAmount must not be null");
        Objects.requireNonNull(valueDate, "valueDate must not be null");
        Objects.requireNonNull(firstRepaymentDate, "firstRepaymentDate must not be null");
        Objects.requireNonNull(confirmedByUserId, "confirmedByUserId must not be null");
        Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");

        externalTransferReference = canonicalReference(externalTransferReference);
        if (expectedContractVersion <= 0) {
            throw invalid("Expected contract version must be positive.");
        }
        if (disbursedAmount.signum() <= 0
                || disbursedAmount.remainder(BigDecimal.ONE).signum() != 0) {
            throw invalid("Disbursed amount must be a positive whole VND amount.");
        }
        validateRepaymentDates(valueDate, firstRepaymentDate);
    }

    public static ManualDisbursement confirmed(
            UUID id,
            LoanContract contract,
            LoanAccount loanAccount,
            UUID requestId,
            int expectedContractVersion,
            String externalTransferReference,
            LocalDate valueDate,
            LocalDate firstRepaymentDate,
            UUID confirmedByUserId,
            LocalDateTime confirmedAt
    ) {
        Objects.requireNonNull(contract, "contract must not be null");
        Objects.requireNonNull(loanAccount, "loanAccount must not be null");
        if (contract.status() != LoanContractStatus.READY_FOR_DISBURSEMENT) {
            throw new BusinessStateConflictException(
                    "MANUAL_DISBURSEMENT_NOT_ALLOWED",
                    "Manual disbursement evidence requires a ready contract."
            );
        }
        if (!loanAccount.loanApplicationId().equals(contract.loanApplicationId())
                || !loanAccount.loanContractId().equals(contract.id())) {
            throw invalid("Loan Account does not belong to the source contract.");
        }
        if (expectedContractVersion != contract.contractVersion()) {
            throw new BusinessStateConflictException(
                    "CONTRACT_VERSION_STALE",
                    "Expected contract version is stale."
            );
        }
        return new ManualDisbursement(
                Objects.requireNonNull(id, "id must not be null"),
                contract.loanApplicationId(),
                contract.id(),
                loanAccount.id(),
                requestId,
                expectedContractVersion,
                externalTransferReference,
                contract.financialTerms().approvedPrincipal(),
                valueDate,
                firstRepaymentDate,
                confirmedByUserId,
                confirmedAt
        );
    }

    public static String canonicalReference(String reference) {
        if (reference == null) {
            throw invalid("External transfer reference is required.");
        }
        String canonical = reference.trim().toUpperCase(Locale.ROOT);
        if (!EXTERNAL_REFERENCE_PATTERN.matcher(canonical).matches()) {
            throw invalid("External transfer reference format is invalid.");
        }
        return canonical;
    }

    public static void validateRepaymentDates(LocalDate valueDate, LocalDate firstRepaymentDate) {
        Objects.requireNonNull(valueDate, "valueDate must not be null");
        Objects.requireNonNull(firstRepaymentDate, "firstRepaymentDate must not be null");
        if (!firstRepaymentDate.isAfter(valueDate)
                || firstRepaymentDate.isAfter(valueDate.plusMonths(1))) {
            throw invalid("First repayment date must be after value date and no later than one calendar month after it.");
        }
    }

    @Override
    public String toString() {
        return "ManualDisbursement[id=" + id + ", loanApplicationId=" + loanApplicationId
                + ", loanContractId=" + loanContractId + ", loanAccountId=" + loanAccountId
                + ", transferEvidence=redacted]";
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("MANUAL_DISBURSEMENT_INVALID", message);
    }
}
