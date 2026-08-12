package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.ApprovedLoanSettlementDto;
import com.meridian.platform.loan.application.port.in.ApproveLoanSettlementUseCase;
import org.springframework.stereotype.Component;

@Component
public class LoanSettlementApiMapper {

    public ApprovedLoanSettlementDto toDto(ApproveLoanSettlementUseCase.Result result) {
        ApproveLoanSettlementUseCase.AccountBalance balance = result.accountBalance();
        return new ApprovedLoanSettlementDto(
                result.loanApplicationId(),
                result.loanAccountId(),
                result.repaymentTransactionId(),
                result.repaymentScheduleId(),
                result.settlementAmount(),
                result.paymentValueDate(),
                result.approvedAt(),
                result.principalAllocated(),
                result.principalReleased(),
                balance.status().name(),
                new ApprovedLoanSettlementDto.AccountBalanceDto(
                        balance.principalPaid(),
                        balance.interestPaid(),
                        balance.feePaid(),
                        balance.totalPaid(),
                        balance.principalOutstanding(),
                        balance.interestOutstanding(),
                        balance.feeOutstanding(),
                        balance.totalOutstanding(),
                        balance.lastPaymentValueDate(),
                        balance.lastPaymentRecordedAt(),
                        balance.servicingEvaluationDate(),
                        balance.status().name()
                ),
                result.idempotentReplay()
        );
    }
}
