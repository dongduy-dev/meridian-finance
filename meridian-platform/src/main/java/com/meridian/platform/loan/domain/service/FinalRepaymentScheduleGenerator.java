package com.meridian.platform.loan.domain.service;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractRepaymentItem;
import com.meridian.platform.loan.domain.model.ManualDisbursement;
import com.meridian.platform.loan.domain.model.RepaymentSchedule;
import com.meridian.platform.loan.domain.model.RepaymentScheduleItem;
import com.meridian.platform.loan.domain.model.RepaymentScheduleType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

public final class FinalRepaymentScheduleGenerator {

    public RepaymentSchedule generate(
            UUID scheduleId,
            List<UUID> scheduleItemIds,
            LoanContract contract,
            LoanAccount loanAccount,
            LocalDate disbursementValueDate,
            LocalDate firstRepaymentDate,
            LocalDateTime generatedAt
    ) {
        Objects.requireNonNull(contract, "contract must not be null");
        Objects.requireNonNull(loanAccount, "loanAccount must not be null");
        Objects.requireNonNull(scheduleItemIds, "scheduleItemIds must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");

        if (!loanAccount.loanApplicationId().equals(contract.loanApplicationId())
                || !loanAccount.loanContractId().equals(contract.id())) {
            throw invalid("Loan Account does not belong to the contract.");
        }
        if (scheduleItemIds.size() != contract.repaymentItems().size()
                || new HashSet<>(scheduleItemIds).size() != scheduleItemIds.size()
                || scheduleItemIds.stream().anyMatch(Objects::isNull)) {
            throw invalid("Repayment schedule item identities are invalid.");
        }

        ManualDisbursement.validateRepaymentDates(disbursementValueDate, firstRepaymentDate);

        int anchorDay = firstRepaymentDate.getDayOfMonth();
        YearMonth firstMonth = YearMonth.from(firstRepaymentDate);
        List<RepaymentScheduleItem> items = IntStream.range(0, contract.repaymentItems().size())
                .mapToObj(index -> copyItem(
                        scheduleItemIds.get(index),
                        contract.repaymentItems().get(index),
                        anchoredDate(firstMonth.plusMonths(index), anchorDay)
                ))
                .toList();

        return new RepaymentSchedule(
                Objects.requireNonNull(scheduleId, "scheduleId must not be null"),
                contract.loanApplicationId(),
                contract.id(),
                loanAccount.id(),
                RepaymentScheduleType.FINAL,
                RepaymentSchedule.INITIAL_FINAL_VERSION,
                contract.financialTerms().approvedTermMonths(),
                contract.financialTerms().approvedPrincipal(),
                contract.financialTerms().totalInterest(),
                contract.financialTerms().feeAmount(),
                contract.financialTerms().totalRepaymentAmount(),
                items.getFirst().dueDate(),
                items.getLast().dueDate(),
                generatedAt,
                items
        );
    }

    private static RepaymentScheduleItem copyItem(
            UUID scheduleItemId,
            LoanContractRepaymentItem source,
            LocalDate dueDate
    ) {
        return new RepaymentScheduleItem(
                scheduleItemId,
                source.id(),
                source.installmentNumber(),
                dueDate,
                source.principalDue(),
                source.interestDue(),
                source.feeDue(),
                source.totalDue()
        );
    }

    private static LocalDate anchoredDate(YearMonth month, int anchorDay) {
        return month.atDay(Math.min(anchorDay, month.lengthOfMonth()));
    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("REPAYMENT_SCHEDULE_INVALID", message);
    }
}
