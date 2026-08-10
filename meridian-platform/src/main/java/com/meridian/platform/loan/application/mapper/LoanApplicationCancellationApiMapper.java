package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.CancelledLoanApplicationDto;
import com.meridian.platform.loan.application.port.in.CancelLoanApplicationUseCase;
import org.springframework.stereotype.Component;

@Component
public class LoanApplicationCancellationApiMapper {

    public CancelledLoanApplicationDto toDto(CancelLoanApplicationUseCase.Result result) {
        return new CancelledLoanApplicationDto(
                result.loanApplicationId(),
                result.resultingStatus().name(),
                result.cancelledAt(),
                result.idempotentReplay()
        );
    }
}
