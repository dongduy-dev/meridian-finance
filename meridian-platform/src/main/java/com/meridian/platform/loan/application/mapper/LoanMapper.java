package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.SalaryAdvanceApplicationCreationResult;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
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
                result.salaryAdvanceVerification().productVerificationResult().name(),
                result.salaryAdvanceVerification().totalLimitSnapshot(),
                result.salaryAdvanceVerification().usedAmountSnapshot(),
                result.salaryAdvanceVerification().reservedAmountSnapshot(),
                result.salaryAdvanceVerification().availableLimitSnapshot(),
                result.loanApplication().submittedAt()
        );
    }

    public UnsecuredConsumerLoanApplicationDto toUnsecuredConsumerLoanApplicationDto(
            LoanApplication application,
            UnsecuredConsumerLoanVerification verification
    ) {
        return new UnsecuredConsumerLoanApplicationDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.status().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                verification.productVerificationResult().name(),
                application.submittedAt()
        );
    }
}
