package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.RecordRepaymentDto;
import com.meridian.platform.loan.application.dto.RepaymentHistoryPageDto;
import com.meridian.platform.loan.application.port.in.QueryRepaymentsUseCase;
import com.meridian.platform.loan.application.port.in.RecordRepaymentUseCase;
import org.springframework.stereotype.Component;

@Component
public class LoanRepaymentApiMapper {

    public RecordRepaymentDto toDto(RecordRepaymentUseCase.Result result) {
        return new RecordRepaymentDto(
                result.loanApplicationId(),
                result.loanAccountId(),
                result.repaymentTransactionId(),
                result.repaymentScheduleId(),
                result.receivedAmount(),
                result.paymentValueDate(),
                result.recordedAt(),
                result.principalAllocatedAndReleased(),
                result.principalAllocatedAndReleased(),
                result.accountBalance().status().name(),
                toBalance(result.accountBalance()),
                result.allocations().stream().map(this::toAllocation).toList(),
                result.installmentProgress().stream().map(this::toInstallment).toList(),
                result.idempotentReplay()
        );
    }

    public RepaymentHistoryPageDto toDto(QueryRepaymentsUseCase.PageResult result) {
        return new RepaymentHistoryPageDto(result.page(), result.size(),
                result.totalElements(), result.totalPages(), result.items().stream()
                .map(this::toHistoryItem).toList());
    }

    private RepaymentHistoryPageDto.ItemDto toHistoryItem(
            QueryRepaymentsUseCase.Item item
    ) {
        return new RepaymentHistoryPageDto.ItemDto(
                item.repaymentTransactionId(),
                item.receivedAmount(),
                item.paymentValueDate(),
                item.recordedAt(),
                item.principalAllocated(),
                item.principalReleased(),
                item.accountStatus().name(),
                toBalance(item.accountBalance()),
                item.allocations().stream().map(this::toAllocation).toList(),
                item.affectedInstallments().stream().map(this::toInstallment).toList()
        );
    }

    private RecordRepaymentDto.AllocationDto toAllocation(
            RecordRepaymentUseCase.Allocation item
    ) {
        return new RecordRepaymentDto.AllocationDto(
                item.sequence(), item.repaymentScheduleItemId(), item.installmentNumber(),
                item.component().name(), item.amount()
        );
    }

    private RecordRepaymentDto.InstallmentOutcomeDto toInstallment(
            RecordRepaymentUseCase.InstallmentProgress item
    ) {
        return new RecordRepaymentDto.InstallmentOutcomeDto(
                item.repaymentScheduleItemId(), item.installmentNumber(), item.dueDate(),
                item.previousStatus().name(), item.status().name(),
                item.servicingEvaluationDate(), item.principalPaid(), item.interestPaid(),
                item.feePaid(), item.totalPaid(), item.principalOutstanding(),
                item.interestOutstanding(), item.feeOutstanding(), item.totalOutstanding(),
                item.lastPaymentValueDate(), item.lastPaymentRecordedAt(), item.statusChanged()
        );
    }

    private static RecordRepaymentDto.AccountBalanceDto toBalance(
            RecordRepaymentUseCase.AccountBalance balance
    ) {
        return new RecordRepaymentDto.AccountBalanceDto(
                balance.principalPaid(), balance.interestPaid(), balance.feePaid(),
                balance.totalPaid(), balance.principalOutstanding(),
                balance.interestOutstanding(), balance.feeOutstanding(),
                balance.totalOutstanding(), balance.lastPaymentValueDate(),
                balance.lastPaymentRecordedAt(), balance.servicingEvaluationDate(),
                balance.status().name()
        );
    }
}
