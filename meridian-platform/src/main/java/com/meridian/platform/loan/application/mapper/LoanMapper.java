package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.SalaryAdvanceApplicationCreationResult;
import org.springframework.stereotype.Component;

@Component
public class LoanMapper {

    public LoanProductDto toLoanProductDto(LoanProduct loanProduct) {
        return new LoanProductDto(
                loanProduct.productCode().name(),
                loanProduct.productType().name(),
                loanProduct.name(),
                loanProduct.description(),
                loanProduct.active(),
                loanProduct.minAmount(),
                loanProduct.maxAmount()
        );
    }

    public SalaryAdvanceApplicationDto toSalaryAdvanceApplicationDto(
            SalaryAdvanceApplicationCreationResult result
    ) {
        return new SalaryAdvanceApplicationDto(
                result.loanApplication().id(),
                result.loanApplication().applicationNumber(),
                result.loanApplication().customerId(),
                result.loanApplication().productCode().name(),
                result.loanApplication().productType().name(),
                result.loanApplication().status().name(),
                result.loanApplication().requestedAmount(),
                result.loanApplication().requestedTermMonths(),
                result.salaryAdvanceVerification().customerPartnerEmployeeLinkId(),
                result.salaryAdvanceLimit().id(),
                result.salaryAdvanceVerification().id(),
                result.salaryAdvanceVerification().productVerificationResult().name(),
                result.salaryAdvanceVerification().totalLimitSnapshot(),
                result.salaryAdvanceVerification().usedAmountSnapshot(),
                result.salaryAdvanceVerification().reservedAmountSnapshot(),
                result.salaryAdvanceVerification().availableLimitSnapshot(),
                result.loanApplication().submittedAt()
        );
    }
}