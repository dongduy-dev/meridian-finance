package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.ClosedLoanAccountDto;
import com.meridian.platform.loan.application.port.in.CloseLoanAccountUseCase;
import org.springframework.stereotype.Component;

@Component
public class LoanAccountClosureApiMapper {

    public ClosedLoanAccountDto toDto(CloseLoanAccountUseCase.Result result) {
        return new ClosedLoanAccountDto(
                result.loanApplicationId(),
                result.loanAccountId(),
                result.resultingStatus().name(),
                result.closedAt(),
                result.idempotentReplay()
        );
    }
}
