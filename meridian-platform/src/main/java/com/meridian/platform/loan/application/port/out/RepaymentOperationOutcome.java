package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentBalance;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentProgress;
import com.meridian.platform.loan.domain.model.RepaymentInstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RepaymentOperationOutcome(
        UUID repaymentTransactionId,
        UUID loanApplicationId,
        UUID loanAccountId,
        UUID repaymentScheduleId,
        BigDecimal receivedAmount,
        LocalDate paymentValueDate,
        LocalDateTime recordedAt,
        RepaymentBalance accountBalance,
        LoanAccountStatus accountStatus,
        boolean accountStatusChanged,
        BigDecimal principalReleased,
        List<InstallmentOutcome> installments
) {

    public RepaymentOperationOutcome {
        Objects.requireNonNull(repaymentTransactionId);
        Objects.requireNonNull(loanApplicationId);
        Objects.requireNonNull(loanAccountId);
        Objects.requireNonNull(repaymentScheduleId);
        Objects.requireNonNull(receivedAmount);
        Objects.requireNonNull(paymentValueDate);
        Objects.requireNonNull(recordedAt);
        Objects.requireNonNull(accountBalance);
        Objects.requireNonNull(accountStatus);
        Objects.requireNonNull(principalReleased);
        installments = List.copyOf(installments);
    }

    public static RepaymentOperationOutcome captured(
            UUID transactionId,
            UUID applicationId,
            UUID accountId,
            UUID scheduleId,
            BigDecimal receivedAmount,
            LocalDate paymentValueDate,
            LocalDateTime recordedAt,
            RepaymentBalance balance,
            LoanAccountStatus status,
            boolean accountStatusChanged,
            BigDecimal principalReleased,
            List<RepaymentInstallmentProgress> previousProgress,
            List<RepaymentInstallmentProgress> progress,
            List<UUID> changedInstallmentIds
    ) {
        Map<UUID, RepaymentInstallmentStatus> previousStatuses = new LinkedHashMap<>();
        previousProgress.forEach(item -> previousStatuses.put(
                item.repaymentScheduleItemId(),
                item.status()
        ));
        return new RepaymentOperationOutcome(
                transactionId, applicationId, accountId, scheduleId,
                receivedAmount, paymentValueDate, recordedAt, balance, status,
                accountStatusChanged, principalReleased,
                progress.stream().map(item -> new InstallmentOutcome(
                        item,
                        Objects.requireNonNull(
                                previousStatuses.get(item.repaymentScheduleItemId()),
                                "Previous installment progress is required."
                        ),
                        changedInstallmentIds.contains(item.repaymentScheduleItemId())
                )).toList()
        );
    }

    public record InstallmentOutcome(
            RepaymentInstallmentProgress progress,
            RepaymentInstallmentStatus previousStatus,
            boolean statusChanged
    ) {
        public InstallmentOutcome {
            Objects.requireNonNull(progress);
            Objects.requireNonNull(previousStatus);
            if (statusChanged == (previousStatus == progress.status())) {
                throw new IllegalArgumentException(
                        "Installment outcome status-change evidence is inconsistent."
                );
            }
        }
    }
}
