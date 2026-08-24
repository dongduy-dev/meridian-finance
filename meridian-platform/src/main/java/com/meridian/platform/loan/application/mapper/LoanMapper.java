package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SubmissionEvidenceRequirementDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.collateral.Collateral;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanVerification;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceApplicationCreationResult;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
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

    public CollateralLoanApplicationDto toCollateralLoanApplicationDto(
            LoanApplication application,
            Collateral collateral,
            CollateralLoanVerification verification,
            LoanDocumentChecklistPort.SubmissionChecklistSnapshot checklist
    ) {
        return new CollateralLoanApplicationDto(
                application.id(),
                application.applicationNumber(),
                application.productCode().name(),
                application.productType().name(),
                application.status().name(),
                application.requestedAmount(),
                application.requestedTermMonths(),
                collateral.collateralType().name(),
                verification.productVerificationResult().name(),
                checklist.items().stream()
                        .map(item -> new SubmissionEvidenceRequirementDto(
                                item.checklistItemId(),
                                item.documentType().name(),
                                item.requirementStatus().name()
                        ))
                        .toList(),
                application.submittedAt()
        );
    }
}
