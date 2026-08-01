package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.DisbursementDestinationRevealDto;
import com.meridian.platform.loan.application.dto.LoanAccountDto;
import com.meridian.platform.loan.application.dto.ManualDisbursementConfirmationDto;
import com.meridian.platform.loan.application.port.in.ConfirmManualDisbursementUseCase;
import com.meridian.platform.loan.application.port.in.QueryLoanAccountUseCase;
import com.meridian.platform.loan.application.port.in.RevealDisbursementDestinationUseCase;
import org.springframework.stereotype.Component;

@Component
public class LoanDisbursementApiMapper {

    public ManualDisbursementConfirmationDto toDto(
            ConfirmManualDisbursementUseCase.Result result
    ) {
        return new ManualDisbursementConfirmationDto(
                result.loanApplicationId(),
                result.applicationStatus().name(),
                result.loanAccountId(),
                result.loanAccountNumber(),
                result.loanAccountStatus().name(),
                result.activatedAt(),
                result.manualDisbursementId(),
                result.disbursedAmount(),
                result.disbursementValueDate(),
                result.firstRepaymentDate(),
                result.repaymentScheduleId(),
                result.scheduleType().name(),
                result.scheduleVersion(),
                result.scheduleItems().stream()
                        .map(item -> new ManualDisbursementConfirmationDto.ScheduleItemDto(
                                item.installmentNumber(),
                                item.dueDate(),
                                item.principalDue(),
                                item.interestDue(),
                                item.feeDue(),
                                item.totalDue()
                        ))
                        .toList(),
                result.idempotentReplay()
        );
    }

    public DisbursementDestinationRevealDto toDto(
            RevealDisbursementDestinationUseCase.Result result
    ) {
        return new DisbursementDestinationRevealDto(
                result.contractId(),
                result.contractVersion(),
                result.bankCode(),
                result.bankName(),
                result.accountHolderName(),
                result.accountNumber()
        );
    }

    public LoanAccountDto toDto(QueryLoanAccountUseCase.Result result) {
        QueryLoanAccountUseCase.DestinationSummary destination = result.destination();
        return new LoanAccountDto(
                result.loanApplicationId(),
                result.loanAccountId(),
                result.accountNumber(),
                result.status().name(),
                result.activatedAt(),
                result.originatedPrincipal(),
                result.approvedTermMonths(),
                result.totalInterest(),
                result.totalFee(),
                result.totalRepayment(),
                new LoanAccountDto.ServicingSummaryDto(
                        result.servicing().principalPaid(),
                        result.servicing().interestPaid(),
                        result.servicing().feePaid(),
                        result.servicing().totalPaid(),
                        result.servicing().principalOutstanding(),
                        result.servicing().interestOutstanding(),
                        result.servicing().feeOutstanding(),
                        result.servicing().totalOutstanding(),
                        result.servicing().servicingEvaluationDate(),
                        result.servicing().lastPaymentValueDate(),
                        result.servicing().lastPaymentRecordedAt()
                ),
                new LoanAccountDto.DestinationSummaryDto(
                        destination.bankCode(),
                        destination.bankName(),
                        destination.accountHolderName(),
                        destination.maskedAccountNumber()
                ),
                new LoanAccountDto.FinalScheduleDto(
                        result.repaymentScheduleId(),
                        result.scheduleType().name(),
                        result.scheduleVersion(),
                        result.firstDueDate(),
                        result.lastDueDate(),
                        result.scheduleItems().stream()
                                .map(item -> new LoanAccountDto.ScheduleItemDto(
                                        item.installmentNumber(),
                                        item.dueDate(),
                                        item.principalDue(),
                                        item.interestDue(),
                                        item.feeDue(),
                                        item.totalDue(),
                                        new LoanAccountDto.InstallmentServicingDto(
                                                item.servicing().principalPaid(),
                                                item.servicing().interestPaid(),
                                                item.servicing().feePaid(),
                                                item.servicing().totalPaid(),
                                                item.servicing().principalOutstanding(),
                                                item.servicing().interestOutstanding(),
                                                item.servicing().feeOutstanding(),
                                                item.servicing().totalOutstanding(),
                                                item.servicing().status().name(),
                                                item.servicing().statusEvaluationDate(),
                                                item.servicing().lastPaymentValueDate(),
                                                item.servicing().lastPaymentRecordedAt()
                                        )
                                ))
                                .toList()
                )
        );
    }
}
